package com.bone.procurement.governance.domain.gateway;

import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import java.util.List;

/** 治理追溯链端口：停机演练 / 状态变更 / 人工终决均留痕。 */
public interface GovernanceTracePort {

  void append(GovernanceTraceEntry entry);

  List<GovernanceTraceEntry> listByCapability(String capabilityCode);
}
