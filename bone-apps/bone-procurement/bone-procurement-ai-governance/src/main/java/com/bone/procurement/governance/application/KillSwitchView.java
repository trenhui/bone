package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.GlobalKillSwitch;

/** kill switch 只读投影。 */
public record KillSwitchView(
    String mode, boolean allowsAi, String operator, String reason, String drillRef) {

  /** 停机演练中单能力的生效态。 */
  public record CapabilityEffect(String code, String state, boolean aiPathActive) {}

  public static KillSwitchView from(GlobalKillSwitch ks) {
    return new KillSwitchView(
        ks.mode().name(), ks.allowsAi(), ks.operator(), ks.reason(), ks.drillRef());
  }
}
