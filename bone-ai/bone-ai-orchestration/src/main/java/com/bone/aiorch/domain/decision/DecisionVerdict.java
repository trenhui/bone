package com.bone.aiorch.domain.decision;

import java.util.List;

/**
 * 确定性闸门判定结果：这次 AI 建议能否自动放行，还是必须转人工。
 *
 * <p><b>为什么必须有这一层</b>：AI 输出是概率性的，直接拿它驱动业务终态等于让模型替业务签字。业界（ClaimSphere 确定性分层 / Guidewire / 蚂蚁
 * DDD）的共同做法是：AI 只产出证据与建议，最终是否自动执行由一个<b>确定性、可解释、可审计</b>的闸门裁决—— 置信度不够、金额太大、模型不可用，一律转人工。本 record
 * 就是该裁决的载体。
 *
 * @param autoApproved 是否允许自动放行（false = 必须人工终审）
 * @param reasons 转人工原因（受控码，便于监控聚合；自动放行时为空）
 */
public record DecisionVerdict(boolean autoApproved, List<String> reasons) {

  /** 置信度低于阈值。 */
  public static final String REASON_CONFIDENCE_LOW = "CONFIDENCE_LOW";

  /** 金额超过人工复核阈值。 */
  public static final String REASON_AMOUNT_HIGH = "AMOUNT_HIGH";

  /** AI 不可用（超时 / 熔断 / 调用失败）——转确定性路径或人工，绝不阻断业务。 */
  public static final String REASON_AI_UNAVAILABLE = "AI_UNAVAILABLE";

  /** 声明了 LLM 步骤但模型未回填置信度——合规 fail-closed 强制人工（拿不出证据就转人工）。 */
  public static final String REASON_CONFIDENCE_MISSING = "CONFIDENCE_MISSING";

  public DecisionVerdict {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  /** 允许自动放行。 */
  public static DecisionVerdict auto() {
    return new DecisionVerdict(true, List.of());
  }

  /** 强制人工终审（可多原因叠加，如置信度不足 + 金额过高）。 */
  public static DecisionVerdict requireHuman(String... reasons) {
    return new DecisionVerdict(false, List.of(reasons));
  }

  /** 是否必须人工终审。 */
  public boolean requiresHuman() {
    return !autoApproved;
  }
}
