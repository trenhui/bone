package com.bone.claim.infrastructure.schedule;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 调度单写者锁记录——<strong>基础设施持久化模型，非业务聚合</strong>（与 {@code IdempotencyRecord} 同构）。
 *
 * <p>多副本部署下，5 个 {@code @Scheduled} Job 此前仅靠 {@code bone.claim.schedule.enabled} 总开关控制，
 * 每个副本都会并发执行，对账/自动关单等产生重复副作用。本记录以「唯一键 {@code lock_name} + {@code @Version}」 在 DB 层实现跨副本互斥：
 *
 * <ul>
 *   <li>首抢 = 插入唯一键；他副本并发插入触发 {@code DataIntegrityViolationException} ⇒ 失败；
 *   <li>续约 = 更新版本；他副本并发更新触发 SDK {@code OptimisticLockingFailureException}（{@code WHERE
 *       version=:old} 命中 0 行）⇒ 失败；
 *   <li>两条竞态路径都 fail-closed 落到「不执行」，绝不静默双跑。
 * </ul>
 *
 * <p><b>为何不用 SELECT ... FOR UPDATE</b>：本仓禁用行锁（改 {@code @Version} 乐观锁，见 §14.3），本服务复用同一机制。
 *
 * <p><b>为何不做成 K8s CronJob 或引 ShedLock</b>：前者要改部署模型且不适配 in-app {@code @Scheduled}，后者本地离线仓库
 * 无该依赖、无法离线构建验证；自建锁表零外部依赖、跨副本稳健、适配 in-app 定时任务（P1-2 落地选型，2026-11-12）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_schedule_lock")
public class ScheduleLock extends AggregateRoot<Long> {

  @Column(name = "lock_name", nullable = false)
  private String lockName;

  @Column(name = "locked_until", nullable = false)
  private Long lockedUntil;

  @Column(name = "locked_at", nullable = false)
  private Long lockedAt;

  @Column(name = "locked_by", nullable = false)
  private String lockedBy;

  /**
   * 乐观锁版本（并发抢占的唯一仲裁者，语义同 {@code ReconciliationDiff.version}）。
   *
   * <p>SDK 遇 {@code @Version} 为 null 会拒绝写入（无法构造 {@code version = :old} 条件），故 {@link #create} 显式置 0，
   * 与 DDL {@code DEFAULT 0} 一致：首次抢占即 0 → 1，与存量行等价，无需数据迁移。
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  private ScheduleLock(String lockName, long lockedUntil, long lockedAt, String lockedBy) {
    setId(DistributedIdGenerator.generateLongId());
    this.lockName = lockName;
    this.lockedUntil = lockedUntil;
    this.lockedAt = lockedAt;
    this.lockedBy = lockedBy;
    this.version = 0L;
  }

  /** 首抢插入：持锁至 {@code lockedUntil}，由 {@code owner} 标识持锁实例。 */
  public static ScheduleLock create(
      String lockName, long lockedUntil, long lockedAt, String lockedBy) {
    return new ScheduleLock(lockName, lockedUntil, lockedAt, lockedBy);
  }

  /** 续约/抢占成功后续约：把持锁窗口推到 {@code lockedUntil}，版本由 SDK 自增。 */
  public void renew(long lockedUntil, long lockedAt, String lockedBy) {
    this.lockedUntil = lockedUntil;
    this.lockedAt = lockedAt;
    this.lockedBy = lockedBy;
  }

  /** 释放：把窗口收回到 {@code now}，使下一轮（或他副本）可立即抢占；仅当 owner 匹配时由服务层调用。 */
  public void release(long now) {
    this.lockedUntil = now;
  }

  /** 是否被 {@code owner} 持有且未到期。 */
  public boolean isHeldBy(String owner, long now) {
    return lockedBy != null && lockedBy.equals(owner) && lockedUntil != null && lockedUntil > now;
  }
}
