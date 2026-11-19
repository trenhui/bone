package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.model.AiCapability;
import com.bone.procurement.governance.domain.model.CapabilityCode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** 受控内存能力登记：walking skeleton 跑通治理链路，生产持久化端口（t_proc_ai_capability DDL 属 L3）就绪后替换。 */
@Component
@ConditionalOnAiGovernanceEnabled
public class InMemoryCapabilityRegistry implements CapabilityRegistry {

  private final ConcurrentMap<String, AiCapability> byCode = new ConcurrentHashMap<>();

  @Override
  public void save(AiCapability capability) {
    byCode.put(capability.code().value(), capability);
  }

  @Override
  public Optional<AiCapability> find(CapabilityCode code) {
    return Optional.ofNullable(byCode.get(code.value()));
  }

  @Override
  public boolean exists(CapabilityCode code) {
    return byCode.containsKey(code.value());
  }

  @Override
  public List<AiCapability> listAll() {
    return List.copyOf(byCode.values());
  }
}
