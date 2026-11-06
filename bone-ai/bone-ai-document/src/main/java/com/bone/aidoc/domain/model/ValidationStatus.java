package com.bone.aidoc.domain.model;

/**
 * 字段规则校验状态（ADR-0046 五层可信度 L2 规则校验的产物）。
 *
 * <p>由 {@link FieldValidationRule} 对单字段产出；{@code FAILED} 表示金额大小写不一致、校验位错误、 日期非法等确定性规则未通过，应直接升级人工复核。
 */
public enum ValidationStatus {
  /** 尚未校验。 */
  PENDING,
  /** 校验通过。 */
  PASSED,
  /** 校验未通过（确定性规则命中）。 */
  FAILED,
  /** 该字段不适用规则（如可选字段缺省）。 */
  NOT_APPLICABLE;

  /** 是否校验失败。 */
  public boolean isFailed() {
    return this == FAILED;
  }

  /** 是否不适用。 */
  public boolean isNotApplicable() {
    return this == NOT_APPLICABLE;
  }

  /** 是否通过。 */
  public boolean isPassed() {
    return this == PASSED;
  }
}
