package com.bone.procurement.transaction.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.transaction.domain.model.requisition.Requisition;
import java.util.List;
import java.util.Optional;

/** 请购单写侧仓储。查询只返回聚合根/标量，符合 ArchUnit E-4.1。 */
public interface RequisitionRepository extends Repository<Requisition, Long> {

  default Optional<Requisition> findByNo(Long tenantId, String prNo) {
    List<Requisition> list =
        findByCriteria(
            Criteria.<Requisition>create()
                .entityClass(Requisition.class)
                .eq("tenantId", tenantId)
                .eq("prNo", prNo));
    return list.stream().findFirst();
  }

  default boolean hasNo(Long tenantId, String prNo) {
    return findByNo(tenantId, prNo).isPresent();
  }

  default long countByNo(Long tenantId, String prNo) {
    return countByCriteria(
        Criteria.<Requisition>create()
            .entityClass(Requisition.class)
            .eq("tenantId", tenantId)
            .eq("prNo", prNo));
  }
}
