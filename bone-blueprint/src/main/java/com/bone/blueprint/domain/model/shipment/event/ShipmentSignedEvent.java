package com.bone.blueprint.domain.model.shipment.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 发货单已签收事件 —— 用于把「货已送达」这一物理事实同步给订单聚合。
 *
 * <p>与 {@link ShipmentShippedEvent} 同源：发货单签收后订单应推进到 DELIVERED， 否则客服看到「订单未送达」而用户已签收，同样是账实不符。
 *
 * @param orderId 关联订单 ID
 * @param tenantId 租户 ID
 * @param shipmentId 发货单 ID
 * @param occurredAt 发生时刻
 */
public record ShipmentSignedEvent(Long orderId, Long tenantId, Long shipmentId, Instant occurredAt)
    implements DomainEvent {}
