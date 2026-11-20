package com.bone.procurement.governance.infrastructure;

import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.GlobalKillSwitchStore;
import com.bone.procurement.governance.domain.model.capability.GlobalKillSwitch;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** 受控内存 kill switch 存储：读库即时生效范式（每次裁决读最新快照），生产持久化属 L3。 */
@Component
@ConditionalOnAiGovernanceEnabled
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
