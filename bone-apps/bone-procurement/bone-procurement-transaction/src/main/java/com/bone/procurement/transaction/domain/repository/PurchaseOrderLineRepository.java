package com.bone.procurement.transaction.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.transaction.domain.model.po.PurchaseOrderLine;
import java.util.List;

/** 订单行仓储（与主表同属聚合，SDK 按实体分别建仓储）。 */
public interface PurchaseOrderLineRepository extends Repository<PurchaseOrderLine, Long> {

  default List<PurchaseOrderLine> findByPoId(Long tenantId, Long poId) {
    return findByCriteria(
        Criteria.<PurchaseOrderLine>create()
            .entityClass(PurchaseOrderLine.class)
            .eq("tenantId", tenantId)
            .eq("poId", poId));
  }
}
