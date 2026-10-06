package com.bone.blueprint.domain.extension.channel;

/**
 * 渠道发货上下文。
 *
 * @param tenantId 租户ID
 * @param channelCode 渠道码
 * @param channelOrderNo 渠道原始订单号
 * @param shipmentNo 内部发货单号
 * @param logisticsCompany 物流公司（内部名，扩展实现负责映射为渠道的物流公司编码）
 * @param trackingNo 运单号
 */
public record ChannelShipmentContext(
    Long tenantId,
    String channelCode,
    String channelOrderNo,
    String shipmentNo,
    String logisticsCompany,
    String trackingNo) {}
