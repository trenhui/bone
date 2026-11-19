package com.bone.procurement.contract.application;

import java.util.Set;

/** HITL 路由判定：是否强制人工复核，以及命中的确定性触发原因。 */
public record HitlRoutingDecision(boolean humanReviewRequired, Set<HitlTrigger> triggers) {

  public HitlRoutingDecision {
    triggers = triggers == null ? Set.of() : Set.copyOf(triggers);
  }
}
