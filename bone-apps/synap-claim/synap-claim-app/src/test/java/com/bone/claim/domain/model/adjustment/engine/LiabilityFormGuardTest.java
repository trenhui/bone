package com.bone.claim.domain.model.adjustment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 责任形式（{@code liability_type}）装载钉子。
 *
 * <p><b>为什么必须有这组钉子</b>：修复前 {@code LiabilityRule.fromLegacy} 把 {@code form} 硬编码成 {@link
 * LiabilityRule.LiabilityForm#REIMBURSEMENT}，于是 {@link LiabilityRule#validate()} 里 {@code
 * LIABILITY_FORM_UNSUPPORTED} 分支在<b>生产路径永不可达</b>——责任配成定额给付/津贴给付型时，引擎会拿 {@code pay_percent}
 * 当报销比例算钱：既不报错，也无从归因。这类缺口比"抛异常"危险得多，因为它在报表里长得像正常赔付。
 *
 * <p><b>两侧都要钉</b>：正向钉存量语义不变（null / 空串仍是报销型，零行为变更），负向钉"非报销型不再被静默计算"。 只钉负向会让"把 null
 * 也拒绝掉"这种过度收紧混进生产（活库 30 条责任里 0 条非报销型，若误伤等于全量停赔）。
 *
 * <p><b>变异锚点</b>：把 {@code build(...)} 的 {@code form} 参数改回常量 {@code REIMBURSEMENT}， {@link
 * #nonReimbursementFormsAreRejectedAtCalculation()} 与 {@link #unknownFormIsRejected()} 必须转红； 把
 * {@code LiabilityForm.of} 的非法值改为回落，{@link #unknownFormIsRejected()} 必须转红。
 */
class LiabilityFormGuardTest {

  private static LiabilityRule legacyWith(String liabilityType) {
    return LiabilityRule.fromLegacy(
        "L-1",
        "v1",
        liabilityType,
        new BigDecimal("0.8"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        false,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        LiabilityRule.DailyBasis.NONE,
        LiabilityRule.LimitedConditions.disabled());
  }

  @Test
  @DisplayName("null / 空串按报销型装配：存量行语义不变，validate 放行")
  void nullOrBlankFallsBackToReimbursement() {
    assertThat(legacyWith(null).form()).isEqualTo(LiabilityRule.LiabilityForm.REIMBURSEMENT);
    assertThat(legacyWith("   ").form()).isEqualTo(LiabilityRule.LiabilityForm.REIMBURSEMENT);
    assertThatCode(() -> legacyWith(null).validate()).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("形式字符串大小写与首尾空白不敏感：' reimbursement ' 仍识别为报销型")
  void formStringIsNormalized() {
    LiabilityRule rule = legacyWith(" reimbursement ");
    assertThat(rule.form()).isEqualTo(LiabilityRule.LiabilityForm.REIMBURSEMENT);
    assertThatCode(rule::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("定额 / 津贴型：形式真的进入规则，理算期拒绝（不再静默按报销比例算钱）")
  void nonReimbursementFormsAreRejectedAtCalculation() {
    for (String type : new String[] {"FIXED_AMOUNT", "ALLOWANCE"}) {
      LiabilityRule rule = legacyWith(type);
      assertThat(rule.form().name()).isEqualTo(type);
      assertThatThrownBy(rule::validate)
          .isInstanceOfSatisfying(
              RuleGap.class,
              gap -> assertThat(gap.code()).isEqualTo(RuleGap.Code.LIABILITY_FORM_UNSUPPORTED))
          .hasMessageContaining("LIABILITY_FORM_UNSUPPORTED");
    }
  }

  @Test
  @DisplayName("配置面仍允许建档：validateConfigShape 不看形式，与 validate 两层分离")
  void configShapeCheckIgnoresForm() {
    assertThatCode(() -> legacyWith("FIXED_AMOUNT").validateConfigShape())
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("非法形式字符串：受控拒绝，而不是回落成报销型")
  void unknownFormIsRejected() {
    assertThatThrownBy(() -> legacyWith("FIXED"))
        .isInstanceOfSatisfying(
            RuleGap.class, gap -> assertThat(gap.code()).isEqualTo(RuleGap.Code.INVALID_INPUT))
        .hasMessageContaining("无法识别");
  }
}
