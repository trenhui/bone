package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.shipment.ShipmentTrace;
import com.bone.blueprint.domain.model.shipment.projection.ShipmentTraceProjection;
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

  /** 读侧通道（E-4.1 ③：仓储读方法返回投影，不返回实体列表）。 */
  public static ShipmentTraceDto from(ShipmentTraceProjection projection) {
    if (projection == null) {
      return null;
    }
    return new ShipmentTraceDto(
        String.valueOf(projection.getId()),
        String.valueOf(projection.getShipmentId()),
        projection.getTraceTime(),
        projection.getTraceStatus(),
        projection.getTraceDesc());
  }
}
