package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.model.RiskLevel;
import java.util.EnumSet;
import java.util.Set;

/**
 * HITL 确定性分层路由（D4）：低置信 / 高后果（HIGH 或 UNKNOWN）/ 命中负向 → 人工复核。
 *
 * <p>规则固定、可审计，LLM 不决定监督强度。常量阈值来自冻结契约；无触发时仅表示"不强制人工"，AI 结论仍是待终决建议。
 */
public final class HitlRoutingPolicy {

  public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.80;

  private final double confidenceThreshold;

  public HitlRoutingPolicy() {
    this(DEFAULT_CONFIDENCE_THRESHOLD);
  }

  public HitlRoutingPolicy(double confidenceThreshold) {
    if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
      throw new IllegalArgumentException("置信度阈值必须在 [0,1]");
    }
    this.confidenceThreshold = confidenceThreshold;
  }

  public HitlRoutingDecision route(double confidence, RiskLevel riskLevel, boolean negativeHit) {
    Set<HitlTrigger> triggers = EnumSet.noneOf(HitlTrigger.class);
    if (confidence < confidenceThreshold) {
      triggers.add(HitlTrigger.LOW_CONFIDENCE);
    }
    if (riskLevel == null || riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.UNKNOWN) {
      triggers.add(HitlTrigger.HIGH_CONSEQUENCE);
    }
    if (negativeHit) {
      triggers.add(HitlTrigger.NEGATIVE_HIT);
    }
    return new HitlRoutingDecision(!triggers.isEmpty(), triggers);
  }

  public double confidenceThreshold() {
    return confidenceThreshold;
  }
}
