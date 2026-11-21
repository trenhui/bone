package com.bone.procurement.risk.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.risk.domain.model.RiskAlert;
import java.util.List;
import java.util.Optional;

/** 风险预警写侧仓储：来源幂等查询落在 default 方法。 */
public interface RiskAlertSqlRepository extends Repository<RiskAlert, Long> {

  /** 来源幂等键：tenant + supplier + source + source_ref。 */
  default Optional<RiskAlert> findBySourceRef(
      Long tenantId, String supplierId, String source, String sourceRef) {
    List<RiskAlert> list =
        findByCriteria(
            Criteria.<RiskAlert>create()
                .entityClass(RiskAlert.class)
                .eq("tenantId", tenantId)
                .eq("supplierId", supplierId)
                .eq("source", source)
                .eq("sourceRef", sourceRef));
    return list.stream().findFirst();
  }
}
