package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.shipment.Shipment;
import java.time.Instant;

/** 发货单出参。 */
public record ShipmentDto(
    String id,
    String orderId,
    String channelCode,
    String shipmentNo,
    String logisticsCompany,
    String trackingNo,
    String status,
    String receiverName,
    String receiverPhone,
    String receiverAddress,
    Boolean channelAck,
    Instant channelAckAt,
    Instant shippedAt,
    Instant signedAt,
    String failReason,
    String remark,
    Instant createdAt,
    Instant updatedAt) {

  public static ShipmentDto from(Shipment entity) {
    if (entity == null) {
      return null;
    }
    return new ShipmentDto(
        String.valueOf(entity.getId()),
        String.valueOf(entity.getOrderId()),
        entity.getChannelCode(),
        entity.getShipmentNo(),
        entity.getLogisticsCompany(),
        entity.getTrackingNo(),
        entity.getStatus() == null ? null : entity.getStatus().name(),
        entity.getReceiverName(),
        entity.getReceiverPhone(),
        entity.getReceiverAddress(),
        entity.getChannelAck(),
        entity.getChannelAckAt(),
        entity.getShippedAt(),
        entity.getSignedAt(),
        entity.getFailReason(),
        entity.getRemark(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
