package com.bone.integration.domain.model.flow.vo;

public enum FlowStatus {
  DRAFT,
  ACTIVE,
  INACTIVE,
  DELETED;

  public boolean isActive() {
    return this == ACTIVE;
  }

  public boolean isDraft() {
    return this == DRAFT;
  }

  public boolean isInactive() {
    return this == INACTIVE;
  }

  public boolean isDeleted() {
    return this == DELETED;
  }
}
