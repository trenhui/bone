package com.bone.claim.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.ClaimContextPort.ClaimContextView;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F1 · 决策图分诊估算额契约测试（防假绿）。
 *
 * <p>{@code LocalRuleEnginePortAdapter#calculatePayout} 返回的是<b>分诊估算额</b>（= 申请金额），仅供决策图 {@code
 * Adjust} 节点做档位判断（大额 → 人工复核），<b>绝非最终结算赔付额</b>。本测试锁定该契约：返回值必须等于申请金额； 若有人把它改成 0、或误当成结算赔付额使用，本测试 +
 * 代码评审应拦住。
 *
 * <p>真实结算必须经领域聚合 {@code Adjustment.calculate(rows, liability)}（含免赔/比例/限额/比例钳制 100% 等护栏）， 当前在生产 main
 * 代码 0 调用 = 结算步骤尚未接线，须随 F1 闭环（见「生产就绪完善/13-理算引擎三重路径收敛与护栏移植」）。
 */
@DisplayName("F1 · LocalRuleEnginePortAdapter 分诊估算额契约")
class LocalRuleEnginePortAdapterTriageEstimateTest {

  private static ClaimContextView ctx(BigDecimal claimAmount) {
    return new ClaimContextView(1001L, "MEDICAL", claimAmount, true, true, BigDecimal.ONE, true);
  }

  @Test
  @DisplayName("calculatePayout 返回申请金额作为分诊估算额（供决策图档位判断，非结算赔付额）")
  void calculatePayout_returnsClaimAmountAsTriageEstimate() {
    LocalRuleEnginePortAdapter adapter = new LocalRuleEnginePortAdapter();
    assertThat(adapter.calculatePayout(ctx(new BigDecimal("888.00"))))
        .isEqualByComparingTo("888.00");
    assertThat(adapter.calculatePayout(ctx(new BigDecimal("12000.00"))))
        .isEqualByComparingTo("12000.00");
  }
}
