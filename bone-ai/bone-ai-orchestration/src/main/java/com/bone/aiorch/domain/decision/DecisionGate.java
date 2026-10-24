package com.bone.aiorch.domain.decision;

import java.math.BigDecimal;

/**
 * 确定性闸门：把「AI 建议」裁决成「能否自动执行」的纯策略对象。
 *
 * <p><b>三条熔断线</b>（对齐 ClaimSphere 确定性网关层与行业惯例）：
 *
 * <ol>
 *   <li><b>置信度</b>：模型自评置信度低于阈值 → 转人工。默认 0.85（比 HITL 待裁决列表的 0.7 更严——0.7 是「值得人看一眼」， 0.85
 *       是「敢不敢自动放行」，两个阈值语义不同，不应混用）；
 *   <li><b>金额</b>：赔付金额超过阈值 → 转人工。默认 5000 元（小额自动、大额人审是理赔行业通行分界线）；
 *   <li><b>AI 可用性</b>：模型超时 / 熔断 / 调用失败 → 转人工（或确定性路径），<b>绝不让 AI 故障阻断业务</b>。
 * </ol>
 *
 * <p><b>纯函数</b>：不依赖 Spring、不读时钟、不做 IO——阈值可配，判定可单测穷举，这是「确定性」的前提。
 *
 * <p>未声明 LLM 步骤时（纯规则决策）直接放行：规则本身是确定性结论，不需要 AI 闸门。置信度为 {@code null} 时<b>强制转人工</b> （合规
 * fail-closed：拿不出证据就转人工，禁止静默自动放行绕过确定性闸门——与 ClaimService 缺置信度默认 0.0 强制人工对齐）。
 * 若某模型确实不产出自评置信度，调用方应改用纯规则决策路径（{@code hasLlmStep=false}）而非假借 null 走自动放行。
 */
public class DecisionGate {

  /** 默认置信度阈值（自动放行需 ≥ 此值）。 */
  public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.85;

  /** 默认金额阈值（超过即人工复核）。 */
  public static final BigDecimal DEFAULT_AMOUNT_THRESHOLD = new BigDecimal("5000");

  private final double confidenceThreshold;
  private final BigDecimal amountThreshold;

  public DecisionGate() {
    this(DEFAULT_CONFIDENCE_THRESHOLD, DEFAULT_AMOUNT_THRESHOLD);
  }

  public DecisionGate(double confidenceThreshold, BigDecimal amountThreshold) {
    if (confidenceThreshold <= 0 || confidenceThreshold > 1) {
      throw new IllegalArgumentException("置信度阈值须在 (0,1]: " + confidenceThreshold);
    }
    if (amountThreshold == null || amountThreshold.signum() < 0) {
      throw new IllegalArgumentException("金额阈值须非负: " + amountThreshold);
    }
    this.confidenceThreshold = confidenceThreshold;
    this.amountThreshold = amountThreshold;
  }

  /**
   * 判定能否自动放行。
   *
   * @param confidence 模型自评置信度（0-1，可为 null = 不可得；声明 LLM 步骤时 null 强制转人工）
   * @param amount 本次赔付金额（可为 null = 非金额场景）
   * @param aiAvailable AI 是否可用（调用失败 / 熔断传 false）
   * @param hasLlmStep 本次编排是否包含 LLM 步骤（false = 纯规则决策，直接放行）
   * @return 判定结果（自动放行或转人工 + 受控原因码）
   */
  public DecisionVerdict evaluate(
      Double confidence, BigDecimal amount, boolean aiAvailable, boolean hasLlmStep) {
    if (!hasLlmStep) {
      // 纯规则决策：规则结果是确定性的，无需 AI 闸门
      return DecisionVerdict.auto();
    }
    if (!aiAvailable) {
      return DecisionVerdict.requireHuman(DecisionVerdict.REASON_AI_UNAVAILABLE);
    }
    // 声明了 LLM 步骤却拿不到模型自评置信度：合规 fail-closed 强制人工。
    // 静默自动放行会让 AI 建议越过确定性闸门，与「拿不出证据就转人工」原则相悖。
    if (confidence == null) {
      return DecisionVerdict.requireHuman(DecisionVerdict.REASON_CONFIDENCE_MISSING);
    }
    boolean confidenceLow = confidence < confidenceThreshold;
    boolean amountHigh = amount != null && amount.compareTo(amountThreshold) > 0;
    if (confidenceLow && amountHigh) {
      return DecisionVerdict.requireHuman(
          DecisionVerdict.REASON_CONFIDENCE_LOW, DecisionVerdict.REASON_AMOUNT_HIGH);
    }
    if (confidenceLow) {
      return DecisionVerdict.requireHuman(DecisionVerdict.REASON_CONFIDENCE_LOW);
    }
    if (amountHigh) {
      return DecisionVerdict.requireHuman(DecisionVerdict.REASON_AMOUNT_HIGH);
    }
    return DecisionVerdict.auto();
  }

  public double confidenceThreshold() {
    return confidenceThreshold;
  }

  public BigDecimal amountThreshold() {
    return amountThreshold;
  }

  /**
   * 对一条 {@link AiSuggestion} 做闸门裁决（便捷重载：从建议取自评置信度）。
   *
   * <p>语义与 {@link #evaluate(Double, BigDecimal, boolean, boolean)} 完全一致（{@code hasLlmStep=true}），
   * 只是免去调用方手工 {@code suggestion.confidence()} 拆包——建议类型进闸门、裁决结论出闸门， 建议本体不越过决策包边界 （ArchUnit 规则 6）。
   */
  public DecisionVerdict adjudicate(
      AiSuggestion<?> suggestion, BigDecimal amount, boolean aiAvailable) {
    return evaluate(suggestion.confidence(), amount, aiAvailable, true);
  }
}
