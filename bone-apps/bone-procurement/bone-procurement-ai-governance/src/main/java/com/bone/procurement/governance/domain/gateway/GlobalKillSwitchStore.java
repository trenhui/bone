package com.bone.procurement.governance.domain.gateway;

import com.bone.procurement.governance.domain.model.capability.GlobalKillSwitch;

/** 全域 kill switch 仓储端口：读库即时生效，无缓存（每次裁决读最新快照）。 */
public interface GlobalKillSwitchStore {

  void save(GlobalKillSwitch killSwitch);

  /** 当前快照；仓储无记录时返回 null，由应用服务按缺省 STANDBY（不放行）处理。 */
  GlobalKillSwitch current();
}
