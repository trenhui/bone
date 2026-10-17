package com.bone.aiorch.application;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.aiorch.domain.decision.DecisionVerdict;
import com.bone.airules.domain.model.RuleEvalResult;
import java.util.List;

/**
 * 编排结果：规则评估明细 + 短路信号 + LLM 响应 + 确定性闸门裁决。
 *
 * @param ruleResults 全部规则评估结果（含未命中 / 灰度外 / 未激活）
 * @param shortCircuited 是否因规则命中触发成本红线（RULE_HIT_BLOCK）
 * @param blockedRuleCode 触发短路的规则码（未短路为 null）
 * @param llmResponse LLM 响应（未声明 LLM 步骤、短路或 AI 不可用时为 null）
 * @param verdict 确定性闸门裁决（能否自动放行 / 转人工原因）；旧调用路径未评估时为 null
 * @param aiError AI 失败原因（AI 不可用时非 null；<b>AI 故障不阻断业务</b>，仅作为降级事实记录）
 */
public record OrchestrationResult(
    List<RuleEvalResult> ruleResults,
    boolean shortCircuited,
    String blockedRuleCode,
    LlmResponse llmResponse,
    DecisionVerdict verdict,
    String aiError) {

  /** 兼容构造（旧调用方直接 new 四参；不携带闸门裁决与 AI 失败事实）。 */
  public OrchestrationResult(
      List<RuleEvalResult> ruleResults,
      boolean shortCircuited,
      String blockedRuleCode,
      LlmResponse llmResponse) {
    this(ruleResults, shortCircuited, blockedRuleCode, llmResponse, null, null);
  }

  /** 兼容构造（不评估闸门；仅供不关心自动放行的旧调用方）。 */
  public static OrchestrationResult of(List<RuleEvalResult> ruleResults, LlmResponse llmResponse) {
    return of(ruleResults, llmResponse, null, null);
  }

  /** 带闸门裁决与 AI 失败事实的完整构造。 */
  public static OrchestrationResult of(
      List<RuleEvalResult> ruleResults,
      LlmResponse llmResponse,
      DecisionVerdict verdict,
      String aiError) {
    RuleEvalResult blocking =
        ruleResults.stream().filter(r -> r.hit() && r.shouldBlockLlm()).findFirst().orElse(null);
    return new OrchestrationResult(
        List.copyOf(ruleResults),
        blocking != null,
        blocking == null ? null : blocking.ruleCode(),
        blocking != null ? null : llmResponse,
        verdict,
        aiError);
  }
}
