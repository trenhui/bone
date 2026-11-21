package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.domain.gateway.GlobalKillSwitchStore;
import com.bone.procurement.governance.domain.model.capability.GlobalKillSwitch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 受控内存 kill switch 存储（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated 端口已由 {@link GlobalKillSwitchStoreAdapter} 委托 SDK 持久化仓储实现；同接口保留 bean 会双 bean。
 */
@Deprecated
public class InMemoryGlobalKillSwitchStore implements GlobalKillSwitchStore {

  private final AtomicReference<GlobalKillSwitch> current = new AtomicReference<>();

  @Override
  public void save(GlobalKillSwitch killSwitch) {
    current.set(killSwitch);
  }

  @Override
  public GlobalKillSwitch current() {
    return current.get();
  }
}
