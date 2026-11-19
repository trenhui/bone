package com.bone.procurement.governance.domain.model;

/**
 * AI 能力运行状态（治理三态 + 不可判定态）。
 *
 * <p>对齐监管「能力清单 + 一键停用/降级」与 fail-closed 红线：只有 {@link #AVAILABLE} 允许被调用；{@link #DISABLED} / {@link
 * #DEGRADED} 属显式停用或降级，{@link #UNKNOWN} / {@link #UNDETERMINED} 是不被信任的缺省态， MUST NOT 被压成「可用」。 {@code
 * UNDETERMINED} 与合同审查上下文的参与态同名但语义独立，代表治理裁决尚不可判定。
 */
public enum CapabilityState {
  AVAILABLE,
  DISABLED,
  DEGRADED,
  UNKNOWN,
  UNDETERMINED;

  /** 是否允许进入 AI 推理路径。仅 AVAILABLE 为 true，其余一律 fail-closed。 */
  public boolean allowsInference() {
    return this == AVAILABLE;
  }

  /** 是否属于不可判定态（不得被当作已停用或已启用放行）。 */
  public boolean isIndeterminate() {
    return this == UNKNOWN || this == UNDETERMINED;
  }
}
