package com.bone.procurement.governance.domain.gateway;

import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import java.util.List;
import java.util.Optional;

/** 人工终决 / override 回流台账端口。 */
public interface HumanDecisionLedger {

  void append(HumanFinalDecision decision);

  Optional<HumanFinalDecision> find(String decisionId);

  /** 按主体查询终决记录，供追溯与评测增补检索。 */
  List<HumanFinalDecision> findBySubject(String subjectRef);

  /** override 回流：按能力码统计推翻次数，供 drift 告警与评测扩样。 */
  long countOverrides(CapabilityCode code);

  /** 按能力码只读返回人工终决记录，供人类监督有效性观测；只读，不写追溯链。 */
  List<HumanFinalDecision> findByCapability(CapabilityCode code);
}
