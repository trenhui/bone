package com.bone.blueprint.domain.model.shipment;

import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 物流轨迹（发货单聚合内实体）。
 *
 * <p><b>为何独立成表而不是塞进 {@link Shipment} 的 JSON 字段</b>：轨迹是<strong>只增不改</strong>的时间序列，
 * 客服要按时间正序展示、风控要查「某运单是否出现过异常退回」。塞进 JSON 后无法按轨迹状态检索， 且每次新增都要整体读改写（并发下覆盖丢失）。独立表让查询与追加都退化成单行操作。
 *
 * <p><b>子实体级仓储登记（E-4.1）</b>：本实体无 {@code @Cascade} 标注，经 {@code ShipmentTraceRepository}
 * 独立存取。三条限制自证：① 只服务 {@code Shipment} 一个聚合根；② 不提升为独立聚合（轨迹无独立业务入口， 只能经 {@code
 * ShipmentApplicationService} 随发货单追加）；③ <b>读侧走投影</b>——仓储读方法返回 {@code ShipmentTraceProjection}（见
 * {@code domain/model/shipment/projection}），不返回实体列表。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_shipment_trace")
public class ShipmentTrace extends TenantAbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long shipmentId;
  private Instant traceTime;
  private String traceStatus;
  private String traceDesc;

  public static ShipmentTrace of(
      long id,
      Long tenantId,
      Long shipmentId,
      Instant traceTime,
      String traceStatus,
      String traceDesc) {
    ShipmentTrace trace = new ShipmentTrace();
    trace.setId(id);
    trace.setTenantId(tenantId);
    trace.shipmentId = shipmentId;
    trace.traceTime = traceTime == null ? Instant.now() : traceTime;
    trace.traceStatus = traceStatus;
    trace.traceDesc = traceDesc;
    // 审计字段必须显式赋值：本实体继承 AbstractEntity，其 created_at / updated_at 是
    // NOT NULL 且 SDK 的 insert 不会代为填充（OrderItem 同理，见其构造函数注释）。
    // 漏填的表现是 DataIntegrityViolationException: Column 'created_at' cannot be null，
    // 被统一异常处理器兜成 500——一个「少赋两个字段」的问题伪装成系统故障。
    Date now = new Date();
    trace.setCreatedAt(now);
    trace.setUpdatedAt(now);
    trace.setDeleted(false);
    return trace;
  }
}
