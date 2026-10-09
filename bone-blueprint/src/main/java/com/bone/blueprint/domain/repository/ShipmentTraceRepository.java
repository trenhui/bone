package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.shipment.ShipmentTrace;
import com.bone.blueprint.domain.model.shipment.projection.ShipmentTraceProjection;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 物流轨迹仓储（SDK 代理实现）。
 *
 * <p><b>子实体级仓储登记（E-4.1）</b>：{@link ShipmentTrace} 无 {@code @Cascade} 标注，走独立仓储 + 同一
 * 应用事务内显式逐条保存。三条限制自证：① 只服务 {@code Shipment} 一个聚合根（{@code shipmentId} 外键归根）； ②
 * 不提升为独立聚合（无独立业务操作入口，轨迹只能随发货单追加）；③ <b>读侧走投影</b>——本仓储的查询方法 只返回 {@link ShipmentTraceProjection}，不返回
 * {@code List<ShipmentTrace>} 实体。
 */
public interface ShipmentTraceRepository extends Repository<ShipmentTrace, Long> {

  /** 某发货单的全部轨迹，按时间正序（客服视角的「物流进度」）。读侧走投影，见类注释 E-4.1 ③。 */
  default List<ShipmentTraceProjection> findByShipment(Long tenantId, Long shipmentId) {
    Criteria<ShipmentTrace> criteria =
        Criteria.<ShipmentTrace>create()
            .eq(ShipmentTrace::getTenantId, tenantId)
            .eq(ShipmentTrace::getShipmentId, shipmentId)
            .orderByAsc(ShipmentTrace::getTraceTime);
    return findByCriteria(criteria).stream().map(ShipmentTraceProjection::from).toList();
  }
}
