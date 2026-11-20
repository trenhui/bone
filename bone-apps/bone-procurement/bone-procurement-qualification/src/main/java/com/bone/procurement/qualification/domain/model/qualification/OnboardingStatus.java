package com.bone.procurement.qualification.domain.model.qualification;

import java.util.Map;
import java.util.Set;

/** 准入单状态：显式迁移表守护；APPROVED/REJECTED 为终态。 */
public enum OnboardingStatus {
  DRAFT,
  IN_REVIEW,
  APPROVED,
  REJECTED;

  private static final Map<OnboardingStatus, Set<OnboardingStatus>> ALLOWED =
      Map.of(
          DRAFT, Set.of(IN_REVIEW),
          IN_REVIEW, Set.of(APPROVED, REJECTED),
          APPROVED, Set.of(),
          REJECTED, Set.of());

  public boolean canTransitionTo(OnboardingStatus target) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }
}
