package com.bone.iam.application.support;

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
 * <p><b>已知边界</b>：该锁只在单 JVM 内有效。多实例部署下仍需 DB 级串行化 —— 要么对 {@code iam_tenant} 目标行 {@code SELECT ... FOR
 * UPDATE}（需新建 JDBC 出站端口，受 {@code sdk-persistence-bypass-baseline.json}「只可收缩、新增文件不得加入」约束 ⇒ L3
 * 待审批），要么为聚合加 {@code version} 乐观锁列（DDL ⇒ L3 待审批）。两者获批前，本锁是<strong>已知且已声明的降级</strong>， 不是完整修复，详见复核报告
 * B-3。
 */
@Service
@RequiredArgsConstructor
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
    long max = tenant.getMaxAccounts();
    withTenantLock(
        tenantId, () -> checkWithinLock(accountRepository.countByTenant(tenantId), max, "账号"));
  }

  public void assertCanAddRole(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxRoles() == null) {
      return;
    }
    long max = tenant.getMaxRoles();
    withTenantLock(
        tenantId, () -> checkWithinLock(roleRepository.countByTenant(tenantId), max, "角色"));
  }

  private void checkWithinLock(long count, long max, String subject) {
    if (count >= max) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, subject + "数已达租户配额上限");
    }
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
