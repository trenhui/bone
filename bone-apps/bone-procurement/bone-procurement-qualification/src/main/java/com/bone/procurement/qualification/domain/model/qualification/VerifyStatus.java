package com.bone.procurement.qualification.domain.model.qualification;

/** 证照核验状态：EXPIRED 不得作为准入通过依据。 */
public enum VerifyStatus {
  PENDING,
  VERIFIED,
  REJECTED,
  EXPIRED;

  public boolean canSupportOnboarding() {
    return this == VERIFIED;
  }
}
