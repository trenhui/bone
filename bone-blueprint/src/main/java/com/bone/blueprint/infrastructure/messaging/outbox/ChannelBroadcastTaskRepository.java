package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 渠道库存广播任务仓储（SDK 代理实现）。
 *
 * <p>中继是<strong>跨租户</strong>扫描（{@code disableTenantFilter()}，与 {@code OrderOutboxRelayPortAdapter}
 * 同构）： 一个调度线程要推进所有租户的待投递任务，租户隔离在逐条投递时由 {@code TenantContextRunner} 重新建立。
 */
public interface ChannelBroadcastTaskRepository extends Repository<ChannelBroadcastTask, Long> {

  /**
   * 待投递批次（含退避时间已到的），按入队时间正序——先进先出，避免新任务被老任务饿死。
   *
   * <p>{@code nextRetryAt <= now} 是退避生效的地方：失败任务被标回 PENDING 时把 {@code nextRetryAt} 推到未来， 若这里不过滤，
   * 退避形同虚设（下一轮立刻又被抢），渠道限流时会被打成更猛的重试风暴。
   */
  default List<ChannelBroadcastTask> findPendingBatch(int limit) {
    return findByCriteria(
        Criteria.<ChannelBroadcastTask>create()
            .eq(ChannelBroadcastTask::getStatus, BroadcastTaskStatus.PENDING)
            .lte(ChannelBroadcastTask::getNextRetryAt, java.time.Instant.now())
            .disableTenantFilter()
            .page(1, limit));
  }

  /**
   * 抢占后的记录——<strong>按本实例 CAS 抢占成功的 id 集合</strong>读回（跨租户，{@code disableTenantFilter()}）。
   *
   * <p><b>为何不能按 {@code status='PROCESSING'} 读全表</b>：多实例部署时，实例 A 抢占的记录在它调渠道 HTTP 期间对实例 B 可见。若按
   * status 读全表， B 会把 A「正在投递中」的任务捞进自己的批次再投一次 ⇒ 同一库存值重复同步到渠道。年龄阈值（{@link #findStuckProcessing}）
   * 只约束「回退误判」，<strong>管不到读回越界</strong>——两条独立路径，只修一条仍会双投（与 {@code
   * OrderOutboxRelayPortAdapter.findClaimed} 同构）。
   *
   * <p>用方法引用而非字符串字段名：字符串会绕过编译期检查，字段名拼错只在运行期抛 {@code UndefinedFieldException}。
   */
  default List<ChannelBroadcastTask> findProcessingByIds(java.util.Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return findByCriteria(
        Criteria.<ChannelBroadcastTask>create()
            .in(ChannelBroadcastTask::getId, ids.toArray())
            .disableTenantFilter());
  }

  /**
   * 卡死的 PROCESSING 记录（上次中继崩溃遗留），按行更新时间早于 {@code before} 判定。
   *
   * <p>用方法引用而非字符串字段名：字符串会绕过编译期检查，字段名拼错只在运行期抛 {@code UndefinedFieldException}。
   */
  default List<ChannelBroadcastTask> findStuckProcessing(java.time.Instant before) {
    return findByCriteria(
        Criteria.<ChannelBroadcastTask>create()
            .eq(ChannelBroadcastTask::getStatus, BroadcastTaskStatus.PROCESSING)
            .lt(ChannelBroadcastTask::getUpdatedAt, before)
            .disableTenantFilter());
  }

  /** 租户下全部状态的广播任务分页（真实 total 由 SDK 提供，前端分页器才能正确显示总数）。 */
  default com.bone.core.model.PageResult<ChannelBroadcastTask> findPageByTenant(
      Long tenantId, int page, int size) {
    return pageByCriteria(
        Criteria.<ChannelBroadcastTask>create()
            .eq(ChannelBroadcastTask::getTenantId, tenantId)
            .orderByDesc(ChannelBroadcastTask::getCreatedAt)
            .page(page, size));
  }

  /** 租户下指定状态的广播任务分页。 */
  default com.bone.core.model.PageResult<ChannelBroadcastTask> findPageByStatus(
      Long tenantId, BroadcastTaskStatus status, int page, int size) {
    return pageByCriteria(
        Criteria.<ChannelBroadcastTask>create()
            .eq(ChannelBroadcastTask::getTenantId, tenantId)
            .eq(ChannelBroadcastTask::getStatus, status)
            .orderByDesc(ChannelBroadcastTask::getCreatedAt)
            .page(page, size));
  }

  /** 租户下指定状态的广播任务（运营页面查询 / 人工重试前校验）。 */
  default List<ChannelBroadcastTask> findByStatus(
      Long tenantId, BroadcastTaskStatus status, int page, int size) {
    return findByCriteria(
        Criteria.<ChannelBroadcastTask>create()
            .eq(ChannelBroadcastTask::getTenantId, tenantId)
            .eq(ChannelBroadcastTask::getStatus, status)
            .orderByDesc(ChannelBroadcastTask::getCreatedAt)
            .page(page, size));
  }
}
