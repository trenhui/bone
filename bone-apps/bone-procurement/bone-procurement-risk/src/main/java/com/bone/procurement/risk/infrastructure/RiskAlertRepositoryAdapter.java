package com.bone.procurement.risk.infrastructure;

import com.bone.procurement.risk.domain.gateway.RiskAlertRepository;
import com.bone.procurement.risk.domain.model.risk.PerformanceScore;
import com.bone.procurement.risk.domain.model.risk.RiskAlert;
import com.bone.procurement.risk.domain.repository.PerformanceScoreSqlRepository;
import com.bone.procurement.risk.domain.repository.RiskAlertSqlRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 风险仓储适配器：实现 domain/gateway 端口，把方法委托给 SDK 仓储。 */
@Component
public class RiskAlertRepositoryAdapter implements RiskAlertRepository {

  private final RiskAlertSqlRepository alerts;
  private final PerformanceScoreSqlRepository scores;

  public RiskAlertRepositoryAdapter(
      RiskAlertSqlRepository alerts, PerformanceScoreSqlRepository scores) {
    this.alerts = alerts;
    this.scores = scores;
  }

  @Override
  public Long save(RiskAlert alert) {
    return alerts.save(alert);
  }

  @Override
  public Optional<RiskAlert> findBySourceRef(
      Long tenantId, String supplierId, String source, String sourceRef) {
    return alerts.findBySourceRef(tenantId, supplierId, source, sourceRef);
  }

  @Override
  public List<PerformanceScore> findScores(Long tenantId, String supplierId) {
    return scores.findScores(tenantId, supplierId);
  }
}
