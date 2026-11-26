package com.bone.procurement.governance.domain.model.capability;

/** 人类监督有效性三态判词（Gartner 2026 approval fatigue：有 HITL 按钮 ≠ 有真实控制）。 */
public enum OversightVerdict {
  UNDETERMINED,
  NO_ACTIONABLE_SIGNAL,
  REVIEW_REQUIRED;

  /** 是否触发 owner 复核；UNKNOWN 语义的 UNDETERMINED 永不可操作。 */
  public boolean isActionable() {
    return this == REVIEW_REQUIRED;
  }
}
