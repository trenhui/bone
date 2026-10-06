package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.shipment.ShipmentTrace;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 物流轨迹仓储（SDK 代理实现）。 */
public interface ShipmentTraceRepository extends Repository<ShipmentTrace, Long> {

  /** 某发货单的全部轨迹，按时间正序（客服视角的「物流进度」）。 */
  default List<ShipmentTrace> findByShipment(Long tenantId, Long shipmentId) {
    Criteria<ShipmentTrace> criteria =
        Criteria.<ShipmentTrace>create()
            .eq(ShipmentTrace::getTenantId, tenantId)
            .eq(ShipmentTrace::getShipmentId, shipmentId)
            .orderByAsc(ShipmentTrace::getTraceTime);
    return findByCriteria(criteria);
  }
}
