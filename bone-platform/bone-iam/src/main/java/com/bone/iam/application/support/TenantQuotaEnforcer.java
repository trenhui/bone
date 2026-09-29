package com.bone.iam.application.support;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.model.tenant.Tenant;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.repository.TenantRepository;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 租户配额校验（S2，仅 CommandHandler 内部复用）。计数委托域仓储（E-4.2）。
 *
 * <p><b>并发不变式（v2 §3.8 第 1 条的 L2 落地形态）</b>：配额校验是典型的「count → 判断 → 写入」序列， 若三者不串行，N 个并发创建会同时读到 {@code
 * count < max} 而一起放行，配额被超卖。本类在<strong>本 JVM 内</strong>按租户取分段锁， 并在事务完成时（{@link
 * TransactionSynchronization#afterCompletion}）才释放，从而把「计数 → 判断 → 插入 → 提交」整段串行化。
 *
 * <p><b>为什么锁要跨到事务结束才放</b>：若在 {@code count} 之后立即解锁，并发事务的 {@code INSERT} 仍会落到锁外， 锁等于没加（这是最容易做出的假绿实现）。
 *
 * <p><b>跨实例串行化（替代原单 JVM 降级）</b>：配额不再走「{@code count} → 判断 → 插入」的非原子序列， 而是把占用数维护在 {@link Tenant} 聚合的
 * {@code allocatedAccounts} / {@code allocatedRoles} 上，写入经 SDK 原生 {@code @Version} 乐观锁 （{@code SET
 * version = version + 1 WHERE version = :old}）。多实例并发下后写者 {@code version} 不匹配， 由 SDK 抛 {@code
 * OptimisticLockingFailureException}，从而杜绝超卖。本 JVM 分段锁保留为单实例快路径，与乐观锁互补。
 */
/**
 * <b>不发 DomainEvent 豁免（E-5.4）</b>：本类对 {@link Tenant} 的写入只推进 {@code allocatedAccounts} / {@code
 * allocatedRoles} 占用计数，属「内部状态迁移」——下游无上下文需感知配额计数变化， 账号/角色的创建与删除事件由各自应用服务负责发布。 若将来接入事件发布，须改为
 * publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class TenantQuotaEnforcer {

  /** 分段锁数量：按租户哈希分散，避免所有租户抢一把全局锁。 */
  private static final int LOCK_STRIPES = 64;

  private final TenantRepository tenantRepository;
  private final AccountRepository accountRepository;
  private final RoleRepository roleRepository;

  private final ReentrantLock[] quotaLocks = newStripedLocks();

  public void assertCanAddAccount(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxAccounts() == null) {
      return;
    }
    withTenantLock(
        tenantId,
        () -> {
          Tenant loaded = tenantRepository.findById(tenantId);
          if (loaded == null || loaded.getMaxAccounts() == null) {
            return;
          }
          if (!loaded.tryAllocateAccount()) {
            throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, "账号数已达租户配额上限");
          }
          tenantRepository.save(loaded);
        });
  }

  public void assertCanAddRole(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxRoles() == null) {
      return;
    }
    withTenantLock(
        tenantId,
        () -> {
          Tenant loaded = tenantRepository.findById(tenantId);
          if (loaded == null || loaded.getMaxRoles() == null) {
            return;
          }
          if (!loaded.tryAllocateRole()) {
            throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, "角色数已达租户配额上限");
          }
          tenantRepository.save(loaded);
        });
  }

  /** 释放一个账号配额（账号删除时调用，与创建在同一事务语义下成对）。 */
  public void releaseAccountQuota(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxAccounts() == null) {
      return;
    }
    withTenantLock(
        tenantId,
        () -> {
          Tenant loaded = tenantRepository.findById(tenantId);
          if (loaded == null) {
            return;
          }
          loaded.releaseAccount();
          tenantRepository.save(loaded);
        });
  }

  /** 释放一个角色配额（角色删除时调用）。 */
  public void releaseRoleQuota(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxRoles() == null) {
      return;
    }
    withTenantLock(
        tenantId,
        () -> {
          Tenant loaded = tenantRepository.findById(tenantId);
          if (loaded == null) {
            return;
          }
          loaded.releaseRole();
          tenantRepository.save(loaded);
        });
  }

  /**
   * 取租户分段锁执行 {@code guarded}，锁的释放点与事务结束对齐。
   *
   * <p>无事务在场时（如单测直接调用）不具备「跨到提交后」的语义，退化为普通同步块，避免锁泄漏。
   */
  private void withTenantLock(long tenantId, Runnable guarded) {
    ReentrantLock lock = quotaLocks[Math.floorMod(Long.hashCode(tenantId), LOCK_STRIPES)];
    lock.lock();
    boolean deferred = deferUnlockUntilCompletion(lock);
    try {
      guarded.run();
    } finally {
      if (!deferred) {
        lock.unlock();
      }
    }
  }

  /** 注册事务同步回调；返回 {@code false} 表示当前无事务，调用方须自行解锁。 */
  private static boolean deferUnlockUntilCompletion(ReentrantLock lock) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return false;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            lock.unlock();
          }
        });
    return true;
  }

  private static ReentrantLock[] newStripedLocks() {
    ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
    for (int i = 0; i < LOCK_STRIPES; i++) {
      locks[i] = new ReentrantLock();
    }
    return locks;
  }
}
