package com.bone.aiorch.application;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.airules.domain.model.RuleEvalResult;
import java.util.List;

/**
 * 编排结果：规则评估明细 + 短路信号 + LLM 响应。
 *
 * @param ruleResults 全部规则评估结果（含未命中 / 灰度外 / 未激活）
 * @param shortCircuited 是否因规则命中触发成本红线（RULE_HIT_BLOCK）
 * @param blockedRuleCode 触发短路的规则码（未短路为 null）
 * @param llmResponse LLM 响应（未声明 LLM 步骤或短路时为 null）
 */
public record OrchestrationResult(
    List<RuleEvalResult> ruleResults,
    boolean shortCircuited,
    String blockedRuleCode,
    LlmResponse llmResponse) {

  public static OrchestrationResult of(List<RuleEvalResult> ruleResults, LlmResponse llmResponse) {
    RuleEvalResult blocking =
        ruleResults.stream().filter(r -> r.hit() && r.shouldBlockLlm()).findFirst().orElse(null);
    return new OrchestrationResult(
        List.copyOf(ruleResults),
        blocking != null,
        blocking == null ? null : blocking.ruleCode(),
        blocking != null ? null : llmResponse);
  }
}
