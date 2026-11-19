package com.bone.procurement.contract.domain.model;

/**
 * AI 参与披露三态。
 *
 * <p>{@code UNKNOWN} / {@code UNDETERMINED} 是独立的 fail-closed 状态，不得被压成布尔 {@code available=false}
 * 或「AI 未参与」；唯一代表「AI 未参与」的取值是 {@link #UNAVAILABLE}。 {@code model_version_snapshot} 有值也不改变本枚举。
 */
public enum AiParticipation {
  AVAILABLE,
  UNAVAILABLE,
  UNKNOWN,
  UNDETERMINED;

  public boolean isAiConfirmed() {
    return this == AVAILABLE;
  }

  public boolean isAiNotParticipated() {
    return this == UNAVAILABLE;
  }

  public boolean isCannotConfirm() {
    return this == UNKNOWN || this == UNDETERMINED;
  }

  /** 原样返回给前端的代码，未知态不翻译、不降级。 */
  public String disclosureCode() {
    return name();
  }
}
