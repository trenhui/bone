package com.bone.procurement.risk.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.risk.domain.gateway.RiskAlertRepository;
import com.bone.procurement.risk.domain.model.risk.PerformanceScore;
import com.bone.procurement.risk.domain.model.risk.RiskAlert;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 风险应用服务（骨架）：预警幂等去重、UNKNOWN 转人工、绩效只读。 */
@Service
@NoDomainEvent
public class RiskApplicationService {

  private final RiskAlertRepository repository;

  public RiskApplicationService(RiskAlertRepository repository) {
    this.repository = repository;
  }

  /** 来源幂等：同 (supplier, source, sourceRef) 重复投递复用既有预警，不新增记录。 */
  public Long raise(
      Long tenantId,
      String supplierId,
      String source,
      String sourceRef,
      String severity,
      String alertType,
      String evidenceRef,
      Date detectedAt) {
    Optional<RiskAlert> existing =
        repository.findBySourceRef(tenantId, supplierId, source, sourceRef);
    if (existing.isPresent()) {
      return existing.get().getId();
    }
    RiskAlert alert =
        RiskAlert.raise(
            tenantId, supplierId, source, sourceRef, severity, alertType, evidenceRef, detectedAt);
    return repository.save(alert);
  }

  public List<PerformanceScore> scores(Long tenantId, String supplierId) {
    return repository.findScores(tenantId, supplierId);
  }
}
