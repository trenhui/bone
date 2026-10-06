package com.bone.blueprint.domain.model.shipment.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 发货单已交运事件 —— 用于把「货已发出」这一物理事实同步给订单聚合。
 *
 * <p><b>为何需要它（真实缺陷的修复）</b>：{@code Shipment} 与 {@code Order} 是两个独立聚合，各有状态机 （Order:
 * CREATED→PAID→SHIPPED→DELIVERED；Shipment: CREATED→SHIPPED→IN_TRANSIT→SIGNED）。 在加入本事件之前，发货流程只推进
 * Shipment 状态，<strong>从不推进 Order 状态</strong>， 于是出现「发货单已 SIGNED、订单仍停在 CREATED」的割裂；更严重的是，
 * <strong>已取消（CANCELLED）的订单也能建发货单并签收</strong>，因为发货链路绕过了 Order 的状态守卫。
 *
 * <p><b>为何用事件而非直接调用</b>：一事务一聚合是本仓硬约束（见 {@code doc/architecture/adr/} 与 {@code
 * AggregatePureUnitTestCoverageTest}）， 在 Shipment 事务里直接写 Order 会跨越聚合边界。事件在聚合内记录、由应用层 AFTER_COMMIT
 * 处理器推进 Order，既保住边界，又让两套状态机保持最终一致。
 *
 * @param orderId关联订单 ID
 * @param tenantId 租户 ID
 * @param shipmentId发货单 ID
 * @param channelCode 渠道码（TAOBAO/JD/DOUYIN/PDD）
 * @param trackingNo 运单号
 * @param occurredAt 发生时刻
 */
public record ShipmentShippedEvent(
    Long orderId,
    Long tenantId,
    Long shipmentId,
    String channelCode,
    String trackingNo,
    Instant occurredAt)
    implements DomainEvent {}
