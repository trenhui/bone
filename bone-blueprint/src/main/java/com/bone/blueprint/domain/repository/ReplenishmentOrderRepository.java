package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.replenishment.ReplenishmentOrder;
import com.bone.blueprint.domain.model.replenishment.valueobject.ReplenishmentStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/** 补货单仓储（SDK 代理实现）。 */
public interface ReplenishmentOrderRepository extends Repository<ReplenishmentOrder, Long> {

  default PageResult<ReplenishmentOrder> findPage(
      Long tenantId, Long productId, ReplenishmentStatus status, int page, int size) {
    Criteria<ReplenishmentOrder> criteria =
        Criteria.<ReplenishmentOrder>create()
            .eq(ReplenishmentOrder::getTenantId, tenantId)
            .eq(productId != null, ReplenishmentOrder::getProductId, productId)
            .eq(status != null, ReplenishmentOrder::getStatus, status)
            .orderByDesc(ReplenishmentOrder::getCreatedAt)
            .page(page, size);
    return pageByCriteria(criteria);
  }

  /** 同商品仓是否已有未完结补货单（防重复下单）。 */
  default ReplenishmentOrder findOpenByProduct(
      Long tenantId, Long productId, String warehouseCode) {
    Criteria<ReplenishmentOrder> criteria =
        Criteria.<ReplenishmentOrder>create()
            .eq(ReplenishmentOrder::getTenantId, tenantId)
            .eq(ReplenishmentOrder::getProductId, productId)
            .eq(ReplenishmentOrder::getWarehouseCode, warehouseCode)
            .in(
                ReplenishmentOrder::getStatus,
                ReplenishmentStatus.DRAFT,
                ReplenishmentStatus.SUBMITTED,
                ReplenishmentStatus.APPROVED);
    return findOneByCriteria(criteria);
  }
}
