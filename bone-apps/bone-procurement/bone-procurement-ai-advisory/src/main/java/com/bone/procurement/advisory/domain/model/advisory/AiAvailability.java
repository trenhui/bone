package com.bone.procurement.advisory.domain.model.advisory;

/** AI 参与三态：只有显式 AVAILABLE 才表示本次确有 AI 参与；UNKNOWN/DEGRADED 不得压成「AI 未参与」。 */
public enum AiAvailability {
  AVAILABLE,
  DEGRADED,
  UNKNOWN;

  public boolean aiActuallyParticipated() {
    return this == AVAILABLE;
  }
}
