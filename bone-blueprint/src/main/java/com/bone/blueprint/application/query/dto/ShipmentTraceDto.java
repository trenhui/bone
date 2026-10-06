package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.shipment.ShipmentTrace;
import java.time.Instant;

/** 物流轨迹出参。 */
public record ShipmentTraceDto(
    String id, String shipmentId, Instant traceTime, String traceStatus, String traceDesc) {

  public static ShipmentTraceDto from(ShipmentTrace entity) {
    if (entity == null) {
      return null;
    }
    return new ShipmentTraceDto(
        String.valueOf(entity.getId()),
        String.valueOf(entity.getShipmentId()),
        entity.getTraceTime(),
        entity.getTraceStatus(),
        entity.getTraceDesc());
  }
}
