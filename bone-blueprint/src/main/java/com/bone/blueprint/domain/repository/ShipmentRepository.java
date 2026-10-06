package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.shipment.Shipment;
import com.bone.blueprint.domain.model.shipment.valueobject.ShipmentStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/** 发货单仓储（SDK 代理实现）。 */
public interface ShipmentRepository extends Repository<Shipment, Long> {

  default Shipment findByOrderId(Long tenantId, Long orderId) {
    Criteria<Shipment> criteria =
        Criteria.<Shipment>create()
            .eq(Shipment::getTenantId, tenantId)
            .eq(Shipment::getOrderId, orderId);
    return findOneByCriteria(criteria);
  }

  default PageResult<Shipment> findPage(
      Long tenantId, String channelCode, ShipmentStatus status, int page, int size) {
    Criteria<Shipment> criteria =
        Criteria.<Shipment>create()
            .eq(Shipment::getTenantId, tenantId)
            .eq(
                channelCode != null && !channelCode.isBlank(),
                Shipment::getChannelCode,
                channelCode)
            .eq(status != null, Shipment::getStatus, status)
            .orderByDesc(Shipment::getCreatedAt)
            .page(page, size);
    return pageByCriteria(criteria);
  }
}
