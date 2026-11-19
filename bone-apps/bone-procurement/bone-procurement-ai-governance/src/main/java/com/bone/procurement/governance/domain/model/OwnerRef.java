package com.bone.procurement.governance.domain.model;

import java.util.Objects;

/**
 * 具名责任人引用（ISO 42001 / EU AI Act Art.14 的 named accountable owner）。
 *
 * <p>风险接受必须由具名责任人书面签署；空责任人不得登记能力，避免「无人负责的 AI」。
 */
public record OwnerRef(String userId, String displayName) {

  public OwnerRef {
    Objects.requireNonNull(userId, "owner.userId");
    if (userId.isBlank()) {
      throw new IllegalArgumentException("责任人 userId 不得为空");
    }
    if (displayName == null) {
      displayName = userId;
    }
  }

  public static OwnerRef of(String userId, String displayName) {
    return new OwnerRef(userId, displayName);
  }
}
