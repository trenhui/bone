package com.bone.claim.domain.model.inspection;

/**
 * 抽检发起方式（§9.5 {@code inspect_type}）。
 *
 * <p>{@link #AUTO} 由 {@code InspectionSamplingJob} 按分层比例抽样生成（操作人记 SYSTEM）；{@link #MANUAL}
 * 由质检主管指定案件发起（典型场景：投诉预警、大额复核）。
 */
public enum InspectionType {
  AUTO,
  MANUAL
}
