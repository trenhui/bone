package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** 受控内存人工终决台账：walking skeleton 用，生产持久化（DDL 属 L3）就绪后替换。 */
@Component
@ConditionalOnAiGovernanceEnabled
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
