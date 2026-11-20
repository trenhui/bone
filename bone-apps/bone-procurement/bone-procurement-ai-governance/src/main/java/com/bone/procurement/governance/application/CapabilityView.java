package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.capability.AiCapability;

/** 能力清单只读投影：对外只暴露状态与原样能力码，不暴露内部可变聚合。 */
public record CapabilityView(
    String code,
    String displayName,
    String state,
    String ownerUserId,
    String ownerDisplayName,
    boolean allowsInference) {

  public static CapabilityView from(AiCapability capability) {
    return new CapabilityView(
        capability.code().value(),
        capability.displayName(),
        capability.state().name(),
        capability.owner().userId(),
        capability.owner().displayName(),
        capability.allowsInference());
  }
}
