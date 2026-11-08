package com.bone.claim.domain.model.adjustment.strategy;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule.LiabilityForm;
import com.bone.claim.domain.model.adjustment.engine.OccurrenceGrouper;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.engine.adjustment.AdjustmentKernelRegistry;
import com.bone.claim.sdk.adjustment.AdjustmentForm;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 理算入口的<b>形式分派 + 回退开关</b>。
 *
 * <p><b>默认关闭，零行为变更</b>：{@link #ROUTING_PROPERTY} 未显式打开时逐字调用旧引擎， 快照、金额、归因行数全部不变。打开后才按 {@code
 * LiabilityRule.form()} 分派。 这是生产资金链路的渐进开关，切换动作须单独评审，不随代码提交自动生效。
 *
 * <p><b>支持矩阵来自纯函数内核</b>：{@link AdjustmentKernelRegistry#supports} 是「哪种形式有金额语义」的唯一
 * 真源；未登记语义的形式在领域策略之外先被拦下，避免将来有人给策略补了个"先按报销算"的回落。
 *
 * <p><b>失败不写业务状态</b>：本类不持有仓储、不发布事件，拒绝时只抛受控 {@link RuleGap}。
 */
@SuppressWarnings("deprecation")
public final class AdjustmentCalculationRouter {

  /** 系统属性开关；默认 false。生产启用须显式配置并跑完双轨回归。 */
  public static final String ROUTING_PROPERTY = "bone.claim.adjustment.strategy-routing.enabled";

  private static final Map<LiabilityForm, AdjustmentStrategy> STRATEGIES = build();

  private AdjustmentCalculationRouter() {}

  private static Map<LiabilityForm, AdjustmentStrategy> build() {
    Map<LiabilityForm, AdjustmentStrategy> map = new EnumMap<>(LiabilityForm.class);
    for (AdjustmentStrategy strategy :
        List.of(
            new ReimbursementAdjustmentStrategy(),
            new FixedAmountAdjustmentStrategy(),
            new AllowanceAdjustmentStrategy())) {
      map.put(strategy.form(), strategy);
    }
    return Map.copyOf(map);
  }

  /** 已登记策略的责任形式（分派表快照，供分派钉子核对三类齐全）。 */
  public static java.util.List<LiabilityForm> registeredForms() {
    return java.util.List.copyOf(STRATEGIES.keySet());
  }

  /** 与旧引擎 {@code calculate(Request)} 同形的入口，供聚合根零改动接入。 */
  public static AdjustmentEngine.Outcome calculate(AdjustmentEngine.Request request) {
    return calculate(request, null);
  }

  public static AdjustmentEngine.Outcome calculate(
      AdjustmentEngine.Request request, List<OccurrenceGrouper.Fact> facts) {
    return calculate(request, facts, Boolean.getBoolean(ROUTING_PROPERTY));
  }

  /**
   * 显式指定开关（供验收两路跑同一批用例，避免测试依赖全局系统属性）。
   *
   * @param routingEnabled false = 旧引擎直连；true = 按责任形式分派
   */
  public static AdjustmentEngine.Outcome calculate(
      AdjustmentEngine.Request request,
      List<OccurrenceGrouper.Fact> facts,
      boolean routingEnabled) {
    if (!routingEnabled || request == null || request.rule() == null) {
      return AdjustmentEngine.calculate(request, facts);
    }

    LiabilityRule rule = request.rule();
    LiabilityForm form = rule.form();
    AdjustmentStrategy strategy = STRATEGIES.get(form);
    if (strategy == null) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "责任形式 %s 未登记理算策略，禁止回落成报销型", form);
    }
    AdjustmentForm sdkForm = AdjustmentForm.of(form == null ? null : form.name());
    if (!AdjustmentKernelRegistry.supports(sdkForm)) {
      throw RuleGap.of(
          RuleGap.Code.LIABILITY_FORM_UNSUPPORTED, "责任形式 %s 在纯函数内核支持矩阵中无金额语义，禁止按报销型公式计算", sdkForm);
    }
    return strategy.calculate(request, facts);
  }
}
