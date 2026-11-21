package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 受控内存能力登记（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated 端口已由 {@link CapabilityRegistryAdapter} 委托 SDK 持久化仓储实现；同接口保留 bean 会双 bean。
 */
@Deprecated
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
