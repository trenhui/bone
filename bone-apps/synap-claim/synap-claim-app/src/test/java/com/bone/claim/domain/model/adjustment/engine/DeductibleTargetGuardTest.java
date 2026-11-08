package com.bone.claim.domain.model.adjustment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 《理算引擎设计》§14.5 裁定的落地钉子：免赔对象（Target）为空必须 fail-closed。
 *
 * <p><b>为什么必须有负向钉子</b>：原实现把 null 隐式回落成 CALCULATED_AMOUNT，而引擎按 target 分两个金额分支 （先乘比例再减免赔 /
 * 先减免赔再乘比例），回落会让赔付额静默偏离且无人察觉。删回落若不配校验，null 绕过 后引擎的分支判断同样会静默按另一口径算——所以「删回落」与「装载期拒绝」必须由同一组测试同时钉住。
 */
class DeductibleTargetGuardTest {

  private static final Map<FeePools.Bucket, BigDecimal> BUCKETS =
      Map.of(FeePools.Bucket.REASONABLE, new BigDecimal("100"));

  @Test
  @DisplayName("perFeeType 传 null target：不再隐式回落，validate 抛 DEDUCTIBLE_TARGET_MISSING")
  void perFeeTypeNullTargetIsRejected() {
    DeductibleRule rule = DeductibleRule.perFeeType(BUCKETS, null);

    assertThat(rule.target()).isNull();
    assertThatThrownBy(rule::validate)
        .isInstanceOfSatisfying(
            RuleGap.class,
            gap -> assertThat(gap.code()).isEqualTo(RuleGap.Code.DEDUCTIBLE_TARGET_MISSING))
        .hasMessageContaining("DEDUCTIBLE_TARGET_MISSING");
  }

  @Test
  @DisplayName("直接构造 null target 的未启用规则：装载期仍硬拒绝，inactive() 不豁免")
  void inactiveRuleWithNullTargetIsStillRejected() {
    DeductibleRule rule =
        new DeductibleRule(
            false,
            DeductibleRule.Kind.ABSOLUTE,
            DeductibleRule.Basis.ANNUAL,
            DeductibleRule.Form.AMOUNT,
            DeductibleRule.Offset.NONE,
            null,
            DeductibleRule.Granularity.LIABILITY,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            Map.of());

    assertThat(rule.inactive()).isTrue();
    assertThatThrownBy(rule::validate)
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("DEDUCTIBLE_TARGET_MISSING");
  }

  @Test
  @DisplayName("显式工厂与显式 target 全部放行（防过度收紧）")
  void explicitTargetsStillPass() {
    assertThatCode(
            () -> {
              DeductibleRule.none().validate();
              DeductibleRule.annualAbsolute(new BigDecimal("100")).validate();
              DeductibleRule.relativeAnnual(new BigDecimal("100")).validate();
              DeductibleRule.perFeeType(BUCKETS, DeductibleRule.Target.CALCULATED_AMOUNT)
                  .validate();
              DeductibleRule.perFeeType(BUCKETS, DeductibleRule.Target.INVOICE_AMOUNT).validate();
            })
        .doesNotThrowAnyException();
  }
}
