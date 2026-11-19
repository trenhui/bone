package com.bone.procurement.governance.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 治理裁决结果：对一次能力调用请求的 fail-closed 裁决。
 *
 * <p>{@code allowed=true} 仅表示允许进入 AI 推理路径，AI 结论永远是建议、不得成为终决（AI 仅辅助红线）；任何判定不出的情况都返回 {@code
 * allowed=false} + 原因码。
 */
public record GovernanceDecision(
    CapabilityCode capabilityCode,
    boolean allowed,
    CapabilityState effectiveState,
    String reasonCode,
    Instant decidedAt) {

  public static final String REASON_ALLOWED = "ALLOWED_ADVISORY_ONLY";
  public static final String REASON_UNKNOWN_CAPABILITY = "UNKNOWN_CAPABILITY";
  public static final String REASON_DISABLED = "CAPABILITY_DISABLED";
  public static final String REASON_DEGRADED = "CAPABILITY_DEGRADED";
  public static final String REASON_INDETERMINATE = "CAPABILITY_INDETERMINATE";

  public GovernanceDecision {
    Objects.requireNonNull(capabilityCode, "capabilityCode");
    Objects.requireNonNull(effectiveState, "effectiveState");
    Objects.requireNonNull(reasonCode, "reasonCode");
    Objects.requireNonNull(decidedAt, "decidedAt");
  }

  /** 缺省拒绝：能力未登记 / 判定不出时统一走这里，杜绝 fail-open。 */
  public static GovernanceDecision deny(
      CapabilityCode code, CapabilityState state, String reasonCode) {
    return new GovernanceDecision(code, false, state, reasonCode, Instant.now());
  }

  public static GovernanceDecision allow(CapabilityCode code) {
    return new GovernanceDecision(
        code, true, CapabilityState.AVAILABLE, REASON_ALLOWED, Instant.now());
  }

  /** 终局护栏：无论裁决如何，AI 都不获得终决权。 */
  public boolean aiOnlyAdvisory() {
    return true;
  }
}
