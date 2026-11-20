package com.bone.procurement.risk.domain.gateway;

import com.bone.procurement.risk.domain.model.PerformanceScore;
import com.bone.procurement.risk.domain.model.RiskAlert;
import java.util.List;
import java.util.Optional;

/** 风险仓储端口（白名单方法）。 */
public interface RiskAlertRepository {
  Long save(RiskAlert alert);

  Optional<RiskAlert> findBySourceRef(
      Long tenantId, String supplierId, String source, String sourceRef);

  List<PerformanceScore> findScores(Long tenantId, String supplierId);
}
