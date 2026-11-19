package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.model.GovernanceTraceEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** 受控内存治理追溯链；生产对接 bone-ai-trace 端口（属 L3 接线）后替换。 */
@Component
@ConditionalOnAiGovernanceEnabled
public class InMemoryGovernanceTracePort implements GovernanceTracePort {

  private final List<GovernanceTraceEntry> entries = new CopyOnWriteArrayList<>();

  @Override
  public void append(GovernanceTraceEntry entry) {
    entries.add(entry);
  }

  @Override
  public List<GovernanceTraceEntry> listByCapability(String capabilityCode) {
    return entries.stream().filter(e -> e.capabilityCode().equals(capabilityCode)).toList();
  }
}
