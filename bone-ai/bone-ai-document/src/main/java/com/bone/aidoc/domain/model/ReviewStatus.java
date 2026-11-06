package com.bone.aidoc.domain.model;

/**
 * 字段级复核状态（ADR-0046 §三.4：可信度应是字段级而非文档级）。
 *
 * <p>每个业务字段独立持有一个复核状态，不能因整张票据识别得好就自动放行其中的金额字段。
 */
public enum ReviewStatus {
  /** 待处理。 */
  PENDING,
  /** 自动通过（高可信，无需人工介入）。 */
  AUTO_PASSED,
  /** 转二次识别（存在疑点，换引擎/换策略再识别一次）。 */
  SECONDARY_RECOGNITION,
  /** 转人工复核（高风险/校验失败/低可信）。 */
  MANUAL_REVIEW
}
