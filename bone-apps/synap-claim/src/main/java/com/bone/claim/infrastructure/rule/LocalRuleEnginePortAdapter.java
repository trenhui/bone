package com.bone.claim.infrastructure.rule;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 规则/理算引擎适配器（一期本地确定性桩）。
 *
 * <p>核责默认无冲突、风控通过、规则置信度 0.90；理算回显申请金额（免赔/封顶/责任比例属二期接责任配置）。 替换只动本类，二期接 QLExpress 热更 + 灰度 +
 * 回归门禁真实引擎（bone-ai C2，§7.3）。
 */
@Component
public class LocalRuleEnginePortAdapter implements RuleEnginePort {

  private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.90");

  @Override
  public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
    // 验真不通过（FAIL/UNVERIFIABLE）即反欺诈硬闸不过（R-FRAUD-06 从严）
    boolean fraudGatePassed = ctx.evidenceAllVerifyPassed();
    return new RuleCheckResult(false, false, fraudGatePassed, RULE_CONFIDENCE);
  }

  @Override
  public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
    // 桩：理算 = 申请金额回显；二期接责任/免赔/封顶确定性计算
    return ctx.claimAmount();
  }
}
