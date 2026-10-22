package com.bone.claim.domain.model.decision;

import java.math.BigDecimal;

/**
 * 决策分诊策略（确定性，无 AI 参与——AI 只产建议，结论由本策略计算）。
 *
 * <p>三条不可让渡的规则：
 *
 * <ol>
 *   <li><b>fail-safe</b>：任何无法判定一律转人工，不得默认通过（§4.3 安全兜底原则）；
 *   <li><b>双阈值独立</b>：OCR 置信度与 AI 置信度是两个独立字段、独立判定，任一命中即转人工（§6.3）；
 *   <li><b>自动通过安全前置</b>：反欺诈硬闸（R-FRAUD）未全部通过即降级转人工，绝不跳过风控硬闸（§6.3）。
 * </ol>
 *
 * <p>大额（≥1 万）不自动通过：走"审核通过待复核"（双人复核），不是自动通过也不是普通人工核赔。
 */
public final class DecisionTriage {

  /** AI 置信度强制人工线（&lt;0.7）。 */
  public static final BigDecimal AI_MANUAL_THRESHOLD = new BigDecimal("0.7");

  /** AI 置信度复核档上界（0.7–0.85 进复核档）。 */
  public static final BigDecimal AI_REVIEW_THRESHOLD = new BigDecimal("0.85");

  /** OCR 置信度独立熔断线（&lt;0.7 强制人工，与 AI 阈值互不影响）。 */
  public static final BigDecimal OCR_MANUAL_THRESHOLD = new BigDecimal("0.7");

  /** 自动通过金额上限（≥1 万不自动通过）。 */
  public static final BigDecimal AUTO_AMOUNT_LIMIT = new BigDecimal("10000");

  private DecisionTriage() {}

  /**
   * 分诊输入。
   *
   * @param hasEvidence 证据是否齐备
   * @param evidenceParsed 证据解析是否成功
   * @param ruleConflict 规则冲突是否无法裁决
   * @param amount 赔付金额（理算结果，null 视为无法判定）
   * @param confidence 有效置信度（AI 成功取 AI 置信度；LLM 降级时取规则路径置信度；null 视为无法判定）
   * @param ocrConfidence OCR 置信度（独立字段，null 视为未提供）
   * @param riskHit 是否命中风险
   * @param fraudGatePassed 反欺诈硬闸是否全部通过
   * @param rejectSuggested 系统是否建议拒赔
   */
  public record Input(
      boolean hasEvidence,
      boolean evidenceParsed,
      boolean ruleConflict,
      BigDecimal amount,
      BigDecimal confidence,
      BigDecimal ocrConfidence,
      boolean riskHit,
      boolean fraudGatePassed,
      boolean rejectSuggested) {}

  /**
   * 分诊结论。
   *
   * @param result 对外 4 值结论
   * @param tier 内部分档（决定后续路由：直出/复核档/强制人工）
   * @param reason 受控原因（禁自由文本，用于 trace 追溯）
   */
  public record Outcome(DecisionResult result, DecisionTier tier, String reason) {}

  /** 分诊：按 fail-safe 优先级依次判定，任一命中即终止。 */
  public static Outcome evaluate(Input in) {
    if (!in.hasEvidence()) {
      return new Outcome(
          DecisionResult.NEED_MORE_EVIDENCE, DecisionTier.MANUAL, "EVIDENCE_MISSING");
    }
    if (!in.evidenceParsed()) {
      return new Outcome(
          DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "EVIDENCE_PARSE_FAILED");
    }
    if (in.ruleConflict()) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "RULE_CONFLICT");
    }
    if (in.amount() == null) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "AMOUNT_UNKNOWN");
    }
    if (in.amount().compareTo(AUTO_AMOUNT_LIMIT) >= 0) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.REVIEW, "AMOUNT_OVER_LIMIT");
    }
    if (in.riskHit()) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "RISK_HIT");
    }
    if (!in.fraudGatePassed()) {
      return new Outcome(
          DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "FRAUD_GATE_NOT_PASSED");
    }
    if (in.ocrConfidence() != null && in.ocrConfidence().compareTo(OCR_MANUAL_THRESHOLD) < 0) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "OCR_CONFIDENCE_LOW");
    }
    if (in.confidence() == null) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "CONFIDENCE_UNKNOWN");
    }
    if (in.confidence().compareTo(AI_MANUAL_THRESHOLD) < 0) {
      return new Outcome(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "AI_CONFIDENCE_LOW");
    }
    if (in.confidence().compareTo(AI_REVIEW_THRESHOLD) < 0) {
      return new Outcome(
          DecisionResult.MANUAL_REQUIRED, DecisionTier.REVIEW, "AI_CONFIDENCE_REVIEW_TIER");
    }
    if (in.rejectSuggested()) {
      return new Outcome(DecisionResult.SUGGEST_REJECT, DecisionTier.MANUAL, "RULE_SUGGEST_REJECT");
    }
    return new Outcome(DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, "ALL_GATES_PASSED");
  }
}
