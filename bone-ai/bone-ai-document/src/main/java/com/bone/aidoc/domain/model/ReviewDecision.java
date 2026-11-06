package com.bone.aidoc.domain.model;

/**
 * 复核路由决策（{@link ReviewPolicy} 对单字段产出的处置指令）。
 *
 * <p>与 {@link ReviewStatus} 对应：决策落到字段上即成为其 {@code reviewStatus}。
 */
public enum ReviewDecision {
  /** 自动通过。 */
  AUTO_PASS,
  /** 二次识别。 */
  SECONDARY_RECOGNIZE,
  /** 人工复核。 */
  MANUAL_REVIEW
}
