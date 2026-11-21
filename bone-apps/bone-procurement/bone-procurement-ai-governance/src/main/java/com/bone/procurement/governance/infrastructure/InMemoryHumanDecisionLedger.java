package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 受控内存人工终决台账（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated 端口已由 {@link HumanDecisionLedgerAdapter} 委托 SDK 持久化仓储实现；同接口保留 bean 会双 bean。
 */
@Deprecated
public class InMemoryHumanDecisionLedger implements HumanDecisionLedger {

  private final ConcurrentMap<String, HumanFinalDecision> byId = new ConcurrentHashMap<>();

  @Override
  public void append(HumanFinalDecision decision) {
    byId.put(decision.decisionId(), decision);
  }

  @Override
  public Optional<HumanFinalDecision> find(String decisionId) {
    return Optional.ofNullable(byId.get(decisionId));
  }

  @Override
  public List<HumanFinalDecision> findBySubject(String subjectRef) {
    return byId.values().stream().filter(d -> d.subjectRef().equals(subjectRef)).toList();
  }

  @Override
  public long countOverrides(CapabilityCode code) {
    return byId.values().stream()
        .filter(d -> d.capabilityCode().equals(code))
        .filter(HumanFinalDecision::isOverride)
        .count();
  }
}
