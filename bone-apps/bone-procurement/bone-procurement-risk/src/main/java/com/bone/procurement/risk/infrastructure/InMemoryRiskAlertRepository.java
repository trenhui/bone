package com.bone.procurement.risk.infrastructure;

import com.bone.procurement.risk.domain.gateway.RiskAlertRepository;
import com.bone.procurement.risk.domain.model.risk.PerformanceScore;
import com.bone.procurement.risk.domain.model.risk.RiskAlert;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受控内存仓储（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated gateway 端口已由 {@link RiskAlertRepositoryAdapter} 委托 SDK 仓储实现；同接口保留 @Repository bean 会双
 *     bean。
 */
@Deprecated
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
