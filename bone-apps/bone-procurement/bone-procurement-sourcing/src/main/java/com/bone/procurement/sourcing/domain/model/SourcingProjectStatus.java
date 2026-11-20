package com.bone.procurement.sourcing.domain.model;

import java.util.Map;
import java.util.Set;

/** 寻源项目状态：显式迁移表守护，未列出的迁移一律拒绝（fail-closed）。 */
public enum SourcingProjectStatus {
  DRAFT,
  PUBLISHED,
  BIDDING_CLOSED,
  AWARDED,
  CLOSED,
  CANCELLED;

  private static final Map<SourcingProjectStatus, Set<SourcingProjectStatus>> ALLOWED =
      Map.of(
          DRAFT, Set.of(PUBLISHED, CANCELLED),
          PUBLISHED, Set.of(BIDDING_CLOSED, CANCELLED),
          BIDDING_CLOSED, Set.of(AWARDED, CANCELLED),
          AWARDED, Set.of(CLOSED),
          CLOSED, Set.of(),
          CANCELLED, Set.of());

  public boolean canTransitionTo(SourcingProjectStatus target) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }

  public boolean isTerminal() {
    return this == CLOSED || this == CANCELLED;
  }

  public boolean isFrozen() {
    return this != DRAFT;
  }
}
