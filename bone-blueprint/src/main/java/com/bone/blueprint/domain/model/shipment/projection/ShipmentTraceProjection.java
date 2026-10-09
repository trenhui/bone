package com.bone.blueprint.domain.model.shipment.projection;

import com.bone.blueprint.domain.model.shipment.ShipmentTrace;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 物流轨迹读模型（子实体投影，E-4.1 ③：子实体读取走投影，不在写仓储上加返回 {@code List<实体>} 的查询方法）。
 *
 * <p>轨迹是只增不改的时间序列，读侧只需 id / 时间 / 状态 / 描述五字段，不暴露 {@link ShipmentTrace} 的写路径（审计字段、{@code @Id}
 * 生成策略均与读模型无关）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShipmentTraceProjection {

  private Long id;
  private Long shipmentId;
  private Instant traceTime;
  private String traceStatus;
  private String traceDesc;

  public ShipmentTraceProjection(
      Long id, Long shipmentId, Instant traceTime, String traceStatus, String traceDesc) {
    this.id = id;
    this.shipmentId = shipmentId;
    this.traceTime = traceTime;
    this.traceStatus = traceStatus;
    this.traceDesc = traceDesc;
  }

  /** 实体 → 投影（Criteria 通道用；{@code @Sql} 通道由 {@code SmartRowMapper} 反射填充）。 */
  public static ShipmentTraceProjection from(ShipmentTrace trace) {
    if (trace == null) {
      return null;
    }
    return new ShipmentTraceProjection(
        trace.getId(),
        trace.getShipmentId(),
        trace.getTraceTime(),
        trace.getTraceStatus(),
        trace.getTraceDesc());
  }
}
