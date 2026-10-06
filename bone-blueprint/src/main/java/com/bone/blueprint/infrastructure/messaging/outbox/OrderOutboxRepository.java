package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 记录仓储——基础设施层仓储。
 *
 * <p>与 {@code domain/repository} 下的领域聚合仓储的区别：领域仓储承载聚合的加载/保存契约， 属于领域模型的一部分；本仓储服务于消息投递的技术状态（Outbox
 * 表），是跨聚合一致性保障的 基础设施机制，不属于业务领域，故随 Outbox 组件一并留在 infrastructure 层。
 */
public interface OrderOutboxRepository extends Repository<OrderOutboxRecord, Long> {

  /**
   * 卡死的 PROCESSING 记录（上次 Relay 崩溃遗留），判据：抢占时刻早于 {@code threshold}。
   *
   * <p><b>为什么必须带阈值</b>：多实例部署下，实例 A 正在投递（网络 IO 可达秒级）的记录对实例 B 可见； 无条件回收全部 PROCESSING 会把「A
   * 正在投递」误判为「崩溃遗留」，抢回 PENDING 造成双投。实现与 {@code ChannelBroadcastTaskRepository.findStuckProcessing}
   * 同构：用行式引用而非字符串字段名，字段名 拼错在编译期就报错。
   *
   * <p><b>claimed_at 为 NULL 的补集</b>：阈值上线的存量 PROCESSING 行（线上表已存在数据）没有抢占时刻， 用 {@code claimedAt IS
   * NULL} 单独捞起一并自愈，否则它们会永久卡死在 PROCESSING。两条件互斥，不存在 重复回收。
   *
   * @param threshold 卡死阈值时刻（{@code now - stuckTimeoutMs}），早于它的 PROCESSING 视为崩溃遗留
   */
  default List<OrderOutboxRecord> findStuckProcessing(Instant threshold) {
    List<OrderOutboxRecord> expired =
        findByCriteria(
            Criteria.<OrderOutboxRecord>create()
                .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
                .lt(OrderOutboxRecord::getClaimedAt, threshold)
                .disableTenantFilter());
    // ↑ Criteria 不支持 OR 拼接，历史数据（claimed_at IS NULL）单独一趟查询合并。
    List<OrderOutboxRecord> legacyNoClaimAt =
        findByCriteria(
            Criteria.<OrderOutboxRecord>create()
                .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
                .isNull(OrderOutboxRecord::getClaimedAt)
                .disableTenantFilter());
    if (legacyNoClaimAt.isEmpty()) {
      return expired;
    }
    var merged = new java.util.ArrayList<OrderOutboxRecord>(expired);
    merged.addAll(legacyNoClaimAt);
    return merged;
  }
}
