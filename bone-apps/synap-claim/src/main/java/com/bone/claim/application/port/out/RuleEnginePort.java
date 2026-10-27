package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 规则/理算引擎端口（出站，对接 bone-ai C2 双引擎：核责规则 + 理算）。
 *
 * <p>一期由 {@code LocalRuleEnginePortAdapter} 以确定性桩应答（无冲突/风控通过/规则置信度 0.90、理算=申请金额回显）； 二期替换为 QLExpress
 * 热更 + 灰度 + 回归门禁的真实引擎。金额计算只走此端口，AI 不碰钱（§4.1）。
 */
public interface RuleEnginePort {

  /** 核责：规则冲突 / 风险命中 / 反欺诈硬闸 / 规则路径置信度。 */
  RuleCheckResult check(ClaimContextPort.ClaimContextView ctx);

  /** 理算：产出核定赔付金额（确定性，单位 DECIMAL(12,2)）。 */
  BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx);

  record RuleCheckResult(
      boolean ruleConflict, boolean riskHit, boolean fraudGatePassed, BigDecimal ruleConfidence) {}
}
