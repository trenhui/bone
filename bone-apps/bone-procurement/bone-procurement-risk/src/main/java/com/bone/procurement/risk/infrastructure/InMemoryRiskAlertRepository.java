package com.bone.procurement.risk.infrastructure;

import com.bone.procurement.risk.domain.gateway.RiskAlertRepository;
import com.bone.procurement.risk.domain.model.risk.PerformanceScore;
import com.bone.procurement.risk.domain.model.risk.RiskAlert;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/** 受控内存仓储：walking skeleton；生产持久化（t_proc_* 已落 DDL）后续替换。 */
@Repository
public class InMemoryRiskAlertRepository implements RiskAlertRepository {

  private final Map<String, RiskAlert> alerts = new ConcurrentHashMap<>();
  private final Map<String, PerformanceScore> scores = new ConcurrentHashMap<>();
  private final AtomicLong seq = new AtomicLong(1);

  @Override
  public Long save(RiskAlert alert) {
    if (alert.getId() == null) alert.setId(seq.getAndIncrement());
    alerts.put(
        key(alert.getTenantId(), alert.getSupplierId(), alert.getSource(), alert.getSourceRef()),
        alert);
    return alert.getId();
  }

  @Override
  public Optional<RiskAlert> findBySourceRef(
      Long tenantId, String supplierId, String source, String sourceRef) {
    return Optional.ofNullable(alerts.get(key(tenantId, supplierId, source, sourceRef)));
  }

  @Override
  public List<PerformanceScore> findScores(Long tenantId, String supplierId) {
    return scores.values().stream()
        .filter(s -> s.getTenantId().equals(tenantId) && supplierId.equals(s.getSupplierId()))
        .toList();
  }

  private String key(Long tenantId, String supplierId, String source, String sourceRef) {
    return tenantId + ":" + supplierId + ":" + source + ":" + sourceRef;
  }
}
