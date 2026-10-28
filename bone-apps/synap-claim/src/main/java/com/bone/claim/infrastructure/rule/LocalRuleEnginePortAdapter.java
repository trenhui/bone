package com.bone.claim.infrastructure.rule;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 规则/理算引擎适配器（一期本地确定性桩）。
 *
 * <p><b>诚实标注</b>：本桩<b>未执行任何规则</b>，故 {@code available=false}——「无冲突/无风险」在此处是默认值而非结论，
 * 不得被下游当作「已核责且干净」。决策图据 {@code available} 把规则这一路并入反欺诈硬闸的 fail-closed 判定（不可用 即无法确认安全 → 升级人工），与视觉 E1 /
 * 图计算 E2 的可用性语义保持一致。
 *
 * <p>核责默认无冲突、风控通过、规则置信度 0.90；理算回显申请金额（免赔/封顶/责任比例属二期接责任配置）。 替换只动本类，二期接 QLExpress 热更 + 灰度 +
 * 回归门禁真实引擎（bone-ai C2，§7.3）；届时 {@code available} 置 {@code true}。
 */
@Component
public class LocalRuleEnginePortAdapter implements RuleEnginePort {

  private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.90");

  @Override
  public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
    // 验真不通过（FAIL/UNVERIFIABLE）即反欺诈硬闸不过（R-FRAUD-06 从严）
    boolean fraudGatePassed = ctx.evidenceAllVerifyPassed();
    // available=false：规则未真实执行，下列布尔为默认值，不可当作已核责结论
    return new RuleCheckResult(false, false, false, fraudGatePassed, RULE_CONFIDENCE);
  }

  @Override
  public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
    // 桩：理算 = 申请金额回显；二期接责任/免赔/封顶确定性计算
    return ctx.claimAmount();
  }
}
