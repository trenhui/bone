package com.bone.claim.infrastructure.schedule;

import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 调度单写者服务（P1-2，2026-11-12 落地）：多副本部署下保证 5 个 {@code @Scheduled} Job 同一时刻仅一个实例执行。
 *
 * <p><b>互斥机制</b>（零外部依赖、跨副本稳健、适配 in-app {@code @Scheduled}）： 以 {@link ScheduleLock} 的「唯一键 {@code
 * lock_name} + {@code @Version}」在 DB 层仲裁——
 *
 * <ul>
 *   <li>首抢 = 插入唯一键；他副本并发插入触发 {@link DataIntegrityViolationException} ⇒ 本实例放弃；
 *   <li>续约 = 更新版本；他副本并发更新触发 SDK {@link OptimisticLockingFailureException} （{@code WHERE version =
 *       :old} 命中 0 行）⇒ 本实例放弃；
 *   <li>两条竞态路径都 fail-closed 落到「不执行」，绝不静默双跑对账/关单。
 * </ul>
 *
 * <p><b>为何不用 SELECT ... FOR UPDATE</b>：本仓禁用行锁（改 {@code @Version} 乐观锁，见 §14.3），本服务复用同一机制。
 *
 * <p><b>租约与崩溃恢复</b>：抢锁时把 {@code locked_until} 推到 {@code now + leaseMinutes}；任务跑完在 {@code finally}
 * 释放（收回到 now）。任务崩溃未释放时，租约到期由另一副本接管——租约须远大于单次任务耗时、远小于任务周期。
 *
 * <p><b>释放边界</b>：{@link #release} 只清本实例持有的锁（{@code locked_by == owner} 才动），绝不越权释放他副本的锁。
 */
@Slf4j
@Component
public class ScheduleLockService {

  private final ScheduleLockRepository repository;

  /** 默认持锁租约（分钟）：远大于单次任务耗时、远小于任务周期；崩溃未释放时到期由他副本接管。 */
  @Value("${bone.claim.schedule.lock-lease-minutes:30}")
  private long defaultLeaseMinutes;

  /** 本实例唯一标识：多副本据此区分持锁者；未显式配置时按进程生成 UUID（K8s 可注入 pod 名以便排障）。 */
  @Value("${bone.claim.schedule.instance-id:}")
  private String configuredInstanceId;

  public ScheduleLockService(ScheduleLockRepository repository) {
    this.repository = repository;
  }

  /** 测试构造：固定租约与实例标识，免去 @Value 与 UUID 不确定性。 */
  ScheduleLockService(ScheduleLockRepository repository, long leaseMinutes, String instanceId) {
    this.repository = repository;
    this.defaultLeaseMinutes = leaseMinutes;
    this.configuredInstanceId = instanceId;
  }

  private String instanceId() {
    if (configuredInstanceId != null && !configuredInstanceId.isBlank()) {
      return configuredInstanceId;
    }
    return "inst-" + UUID.randomUUID();
  }

  /**
   * 单写者执行：当且仅当本实例抢到锁时运行 {@code task}，运行后在 {@code finally} 释放；未抢到（他副本持有且未到期）跳过。
   *
   * @return 是否实际执行了 {@code task}
   */
  public boolean execute(String lockName, Runnable task) {
    return execute(lockName, defaultLeaseMinutes, task);
  }

  /** 带显式租约的单写者执行（按任务周期微调，如每分钟的 outbox 中继用较短租约）。 */
  public boolean execute(String lockName, long leaseMinutes, Runnable task) {
    String owner = instanceId();
    if (!acquire(lockName, leaseMinutes, owner)) {
      log.debug("[调度单写者] 未抢到锁，跳过: lockName={}, owner={}", lockName, owner);
      return false;
    }
    try {
      task.run();
      return true;
    } finally {
      release(lockName, owner);
    }
  }

  /**
   * @return 是否抢到锁（含首抢与续约两种成功路径）。
   */
  boolean acquire(String lockName, long leaseMinutes, String owner) {
    long now = System.currentTimeMillis();
    long until = now + leaseMinutes * 60_000L;
    List<ScheduleLock> existing =
        repository.findByCriteria(
            Criteria.<ScheduleLock>create().eq(ScheduleLock::getLockName, lockName).page(1, 1));
    if (existing == null || existing.isEmpty()) {
      try {
        repository.save(ScheduleLock.create(lockName, until, now, owner));
        return true;
      } catch (DataIntegrityViolationException e) {
        // 并发首抢：他副本已插入同 lock_name（唯一键冲突），保留其持锁 ⇒ 本实例失败（不执行）。
        log.debug("[调度单写者] 插入唯一键冲突（并发首抢），放弃: lockName={}", lockName);
        return false;
      }
    }
    ScheduleLock lock = existing.get(0);
    if (lock.getLockedUntil() != null && lock.getLockedUntil() > now) {
      return false; // 他副本持有且未到期
    }
    lock.renew(until, now, owner);
    try {
      repository.save(lock); // WHERE version=:old，冲突 ⇒ OptimisticLockingFailureException
      return true;
    } catch (OptimisticLockingFailureException e) {
      // 并发续约：他副本已先一步续约（版本条件命中 0 行），放弃。
      log.debug("[调度单写者] 版本冲突（并发续约），放弃: lockName={}", lockName);
      return false;
    }
  }

  /** 仅释放本实例持有的锁；他副本持有的锁一律不动。 */
  void release(String lockName, String owner) {
    long now = System.currentTimeMillis();
    List<ScheduleLock> existing =
        repository.findByCriteria(
            Criteria.<ScheduleLock>create().eq(ScheduleLock::getLockName, lockName).page(1, 1));
    if (existing == null || existing.isEmpty()) {
      return;
    }
    ScheduleLock lock = existing.get(0);
    if (!owner.equals(lock.getLockedBy())) {
      return; // 只释放自己持有的锁，绝不越权释放他副本的锁
    }
    lock.release(now);
    try {
      repository.save(lock);
    } catch (OptimisticLockingFailureException ignored) {
      // 释放窗口内锁已被他副本接管 ⇒ 无需再动。
    }
  }
}
