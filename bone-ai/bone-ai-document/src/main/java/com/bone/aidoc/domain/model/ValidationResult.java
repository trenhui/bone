package com.bone.aidoc.domain.model;

/**
 * 字段规则校验结果（ADR-0046 五层可信度 L2 产物）。
 *
 * @param status 校验状态（PASSED / FAILED / NOT_APPLICABLE）
 * @param message 说明（FAILED 时给出原因，便于人工复核与审计）
 */
public record ValidationResult(ValidationStatus status, String message) {

  /** 通过（无说明）。 */
  public static ValidationResult passed() {
    return new ValidationResult(ValidationStatus.PASSED, "");
  }

  /** 不适用（字段缺省或规则不覆盖）。 */
  public static ValidationResult notApplicable() {
    return new ValidationResult(ValidationStatus.NOT_APPLICABLE, "");
  }

  /** 失败（带原因）。 */
  public static ValidationResult failed(String message) {
    return new ValidationResult(ValidationStatus.FAILED, message);
  }
}
