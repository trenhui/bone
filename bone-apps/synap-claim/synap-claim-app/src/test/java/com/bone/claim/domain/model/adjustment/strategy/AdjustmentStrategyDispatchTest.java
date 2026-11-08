package com.bone.claim.domain.model.adjustment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 责任形式 → 策略的枚举分派钉子。
 *
 * <p><b>为什么三类都要有落点</b>：只实现报销型、把其余形式用 {@code else} 兜住，等于把「没定义金额语义」 和「配置写错」混成一条路径。三类各自登记后，将来补定额 /
 * 津贴规则时改一个实现类即可，分派表不动。
 *
 * <p><b>变异锚点</b>：把 {@code AdjustmentKernelRegistry.supports} 改成恒 true，或把非报销型策略改成 委托旧引擎，本类的 {@link
 * #unsupportedFormsAreRejectedBeforeCalculation()} 必须转红。
 */
@SuppressWarnings("deprecation")
class AdjustmentStrategyDispatchTest {

  private static AdjustmentEngine.Request request(String form) {
    List<ClaimWithItemsProjection> rows = new ArrayList<>();
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(1001L);
    p.setClaimNo("CL001");
    p.setItemId(2001L);
    p.setItemName("门诊费");
    p.setItemSocialFlag("IN");
    p.setItemValidAmount(new BigDecimal("1000.00"));
    p.setItemSelfPay1(new BigDecimal("1000.00"));
    rows.add(p);

    LiabilityRule rule =
        LiabilityRule.fromLegacy(
            "L-1",
            "v1",
            form,
            new BigDecimal("0.8"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LiabilityRule.DailyBasis.NONE,
            LiabilityRule.LimitedConditions.disabled());
    return AdjustmentEngine.Request.of(1001L, rows, rule, Accumulation.FIRST);
  }

  @Test
  @DisplayName("分派表登记三类责任形式，且策略与形式一一对应")
  void threeFormsAreRegistered() {
    assertThat(AdjustmentCalculationRouter.registeredForms())
        .containsExactlyInAnyOrder(
            LiabilityRule.LiabilityForm.REIMBURSEMENT,
            LiabilityRule.LiabilityForm.FIXED_AMOUNT,
            LiabilityRule.LiabilityForm.ALLOWANCE);
    assertThat(new ReimbursementAdjustmentStrategy().form())
        .isEqualTo(LiabilityRule.LiabilityForm.REIMBURSEMENT);
    assertThat(new FixedAmountAdjustmentStrategy().form())
        .isEqualTo(LiabilityRule.LiabilityForm.FIXED_AMOUNT);
    assertThat(new AllowanceAdjustmentStrategy().form())
        .isEqualTo(LiabilityRule.LiabilityForm.ALLOWANCE);
  }

  @Test
  @DisplayName("报销型：开关打开后经分派仍走九步主链，金额非零")
  void reimbursementIsDispatchedToLegacyChain() {
    AdjustmentEngine.Outcome outcome =
        AdjustmentCalculationRouter.calculate(request("REIMBURSEMENT"), null, true);
    assertThat(outcome.totalAmount().signum()).isGreaterThan(0);
  }

  @Test
  @DisplayName("定额 / 津贴型：进入金额计算前即受控拒绝，绝不按报销比例算钱")
  void unsupportedFormsAreRejectedBeforeCalculation() {
    for (String form : new String[] {"FIXED_AMOUNT", "ALLOWANCE"}) {
      assertThatThrownBy(() -> AdjustmentCalculationRouter.calculate(request(form), null, true))
          .isInstanceOfSatisfying(
              RuleGap.class,
              gap -> assertThat(gap.code()).isEqualTo(RuleGap.Code.LIABILITY_FORM_UNSUPPORTED))
          .hasMessageContaining("禁止按报销型公式计算");
    }
  }

  @Test
  @DisplayName("领域策略自身也是第二道防线：直接调用非报销型策略同样拒绝")
  void unsupportedStrategiesFailClosedStandalone() {
    for (AdjustmentStrategy strategy :
        List.of(new FixedAmountAdjustmentStrategy(), new AllowanceAdjustmentStrategy())) {
      assertThatThrownBy(() -> strategy.calculate(null, null))
          .isInstanceOfSatisfying(
              RuleGap.class,
              gap -> assertThat(gap.code()).isEqualTo(RuleGap.Code.LIABILITY_FORM_UNSUPPORTED));
    }
  }

  @Test
  @DisplayName("未知形式：装载期受控拒绝，不回落成报销型")
  void unknownFormIsRejectedAtLoad() {
    assertThatThrownBy(() -> request("FIXED"))
        .isInstanceOfSatisfying(
            RuleGap.class, gap -> assertThat(gap.code()).isEqualTo(RuleGap.Code.INVALID_INPUT))
        .hasMessageContaining("无法识别");
  }

  @Test
  @DisplayName("回退开关关闭：null 请求交由旧引擎的入参校验处理（零行为变更）")
  void routingOffDelegatesStraightToLegacyEngine() {
    assertThatThrownBy(() -> AdjustmentCalculationRouter.calculate(null, null, false))
        .isInstanceOf(RuleGap.class)
        .describedAs("旧引擎 requireInputs 抛的是受控 RuleGap，不是 NPE");
  }
}
