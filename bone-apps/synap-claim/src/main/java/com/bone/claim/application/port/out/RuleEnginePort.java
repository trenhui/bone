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

  /**
   * 核责结论。
   *
   * <p><b>安全契约</b>：{@code available} 表示「本次是否真实执行过规则核责」——它是反欺诈硬闸三路信号中 **规则**这一路的可用性凭据。本地桩未执行任何规则时必须为
   * {@code false}，<b>不可用绝不等同于无冲突/无风险</b>； 决策图据此 fail-closed 升级人工（与 {@code
   * VisualForensicsPort}/{@code GraphFraudPort} 的可用性语义对齐）。 缺此字段时，`ruleConflict=false`
   * 会被误读为「已核责且无冲突」，属静默放行风险。
   *
   * @param available 本次是否真实执行规则核责（本地桩=false；bone-ai C2 接入后=true）
   * @param ruleConflict 规则冲突无法裁决（仅当 {@code available=true} 时可信）
   * @param riskHit 规则侧命中风险（仅当 {@code available=true} 时可信）
   * @param fraudGatePassed 规则侧反欺诈前置（证据验真）是否通过（仅当 {@code available=true} 时可信）
   * @param ruleConfidence 规则路径置信度（仅当 {@code available=true} 时可信）
   */
  record RuleCheckResult(
      boolean available,
      boolean ruleConflict,
      boolean riskHit,
      boolean fraudGatePassed,
      BigDecimal ruleConfidence) {}
}
