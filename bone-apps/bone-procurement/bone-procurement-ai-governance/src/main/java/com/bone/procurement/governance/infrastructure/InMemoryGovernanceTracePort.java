package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 受控内存治理追溯链（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated 端口已由 {@link GovernanceTraceAdapter} 委托 SDK 持久化仓储实现；同接口保留 bean 会双 bean。
 */
@Deprecated
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
