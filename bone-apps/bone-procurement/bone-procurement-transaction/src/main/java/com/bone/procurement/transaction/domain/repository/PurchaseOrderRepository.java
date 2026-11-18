package com.bone.procurement.transaction.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.transaction.domain.model.po.PurchaseOrder;
import java.util.List;
import java.util.Optional;

/** 采购订单写侧仓储。 */
public interface PurchaseOrderRepository extends Repository<PurchaseOrder, Long> {

  default Optional<PurchaseOrder> findByNo(Long tenantId, String poNo) {
    List<PurchaseOrder> list =
        findByCriteria(
            Criteria.<PurchaseOrder>create()
                .entityClass(PurchaseOrder.class)
                .eq("tenantId", tenantId)
                .eq("poNo", poNo));
    return list.stream().findFirst();
  }

  default boolean hasNo(Long tenantId, String poNo) {
    return findByNo(tenantId, poNo).isPresent();
  }

  /** 订单列表分页：按租户锚定，可选状态过滤，按创建时间倒序。只读投影，不跨租户。 */
  default PageResult<PurchaseOrder> findPage(Long tenantId, String status, int page, int size) {
    Criteria<PurchaseOrder> criteria =
        Criteria.<PurchaseOrder>create()
            .entityClass(PurchaseOrder.class)
            .eq("tenantId", tenantId)
            .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
            .orderByDesc("createdAt")
            .page(page, size);
    return pageByCriteria(criteria);
  }
}
