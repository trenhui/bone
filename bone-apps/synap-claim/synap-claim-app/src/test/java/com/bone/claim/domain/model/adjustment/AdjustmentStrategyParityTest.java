package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Oracle;
import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Row;
import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Scenario;
import com.bone.claim.domain.model.adjustment.strategy.AdjustmentCalculationRouter;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.engine.adjustment.AdjustmentKernelRegistry;
import com.bone.claim.sdk.adjustment.AdjustmentCommand;
import com.bone.claim.sdk.adjustment.AdjustmentForm;
import com.bone.claim.sdk.adjustment.AdjustmentReceipt;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 形式分派开关的金额等价三轨（双轨对拍 / 缺口哨兵 / 变异鉴别）。
 *
 * <p><b>本类回答的问题</b>：把入口从「旧引擎直连」换成「按责任形式分派」后，历史案件的金额、快照与
 * 快照哈希是否逐字不变。只跑新路径单测无法排除「分派时换了条公式」这类改动，必须拿既有双轨 oracle 与 19 份变异体对撞。
 *
 * <p><b>第三轨（纯函数内核）</b>：{@code adjustment-engine} 的 {@link
 * com.bone.claim.engine.adjustment.ReimbursementKernel} 是独立模块里的标量实现，不复用生产工具方法。它与 oracle
 * 在全部场景一致，证明新内核的链序与精度 没有偷偷分叉；把任一步改坏（免赔归零）必须至少在一个场景上被识别。
 */
@SuppressWarnings("deprecation")
class AdjustmentStrategyParityTest {

  @AfterEach
  void clearRoutingProperty() {
    System.clearProperty(AdjustmentCalculationRouter.ROUTING_PROPERTY);
  }

  private static Adjustment adjust(Scenario s, long id, boolean routingEnabled) {
    System.setProperty(
        AdjustmentCalculationRouter.ROUTING_PROPERTY, String.valueOf(routingEnabled));
    List<ClaimWithItemsProjection> rows = new ArrayList<>();
    long itemId = 2001L;
    for (Row r : s.rows()) {
      ClaimWithItemsProjection p = new ClaimWithItemsProjection();
      p.setClaimId(AdjustmentDualTrackFixture.CLAIM);
      p.setClaimNo("CL001");
      p.setItemId(r.headOnly() ? null : itemId++);
      p.setItemName(r.name());
      p.setItemSocialFlag(r.socialFlag());
      p.setItemValidAmount(r.valid());
      p.setItemSelfPay1(r.selfPay1());
      rows.add(p);
    }
    AdjLiability liability =
        AdjLiability.of(
            1L,
            AdjustmentDualTrackFixture.TENANT,
            "L-DUAL",
            "v1",
            7L,
            "R01",
            "REIMBURSEMENT",
            s.payPercent(),
            s.deduct(),
            s.limit(),
            s.socialOnly() ? 1 : 0,
            0,
            "ACTIVE");
    return Adjustment.calculate(
        id,
        AdjustmentDualTrackFixture.TENANT,
        AdjustmentDualTrackFixture.CLAIM,
        rows,
        liability,
        "r1",
        Accumulation.FIRST);
  }

  @Test
  @DisplayName("双轨对拍：开关两路金额、快照、哈希逐字一致，且等于参考 oracle")
  void routingOffAndOnAreEquivalentAcrossAllScenarios() {
    List<Scenario> scenarios = AdjustmentDualTrackFixture.scenarios();
    assertThat(scenarios).isNotEmpty();
    for (Scenario s : scenarios) {
      Adjustment off = adjust(s, 9001L, false);
      Adjustment on = adjust(s, 9001L, true);
      BigDecimal expected = AdjustmentDualTrackFixture.REFERENCE.payout(s);

      assertThat(on.getTotalAmount())
          .as("场景 %s：开关打开后金额不得漂移", s.label())
          .isEqualByComparingTo(expected);
      assertThat(off.getTotalAmount())
          .as("场景 %s：回退路径金额不得漂移", s.label())
          .isEqualByComparingTo(expected);
      assertThat(on.getFormulaSnapshot())
          .as("场景 %s：快照逐字一致", s.label())
          .isEqualTo(off.getFormulaSnapshot());
      assertThat(on.getFormulaHash()).as("场景 %s：快照哈希一致", s.label()).isEqualTo(off.getFormulaHash());
    }
  }

  @Test
  @DisplayName("变异鉴别：开关打开后 19 份被改坏的 oracle 仍全部被识别")
  void routingOnStillDetectsEveryMutant() {
    List<Scenario> scenarios = AdjustmentDualTrackFixture.scenarios();
    List<String> blindSpots = new ArrayList<>();
    for (Map.Entry<String, Oracle> e : AdjustmentDualTrackFixture.mutants().entrySet()) {
      boolean caught = false;
      for (Scenario s : scenarios) {
        BigDecimal actual = adjust(s, 9001L, true).getTotalAmount();
        if (actual.compareTo(e.getValue().payout(s)) != 0) {
          caught = true;
          break;
        }
      }
      if (!caught) {
        blindSpots.add(e.getKey());
      }
    }
    assertThat(blindSpots).as("以下变异在新路径上零场景可辨：%s", blindSpots).isEmpty();
  }

  @Test
  @DisplayName("第三轨：纯函数内核在全部场景与参考 oracle 金额一致")
  void kernelMatchesReferenceAcrossAllScenarios() {
    for (Scenario s : AdjustmentDualTrackFixture.scenarios()) {
      AdjustmentReceipt receipt = kernel(s, s.deduct());
      assertThat(receipt.totalAmount())
          .as("场景 %s：内核金额须与 oracle 一致", s.label())
          .isEqualByComparingTo(AdjustmentDualTrackFixture.REFERENCE.payout(s));
      assertThat(receipt.steps()).as("场景 %s：计算链轨迹不得为空", s.label()).isNotEmpty();
    }
  }

  @Test
  @DisplayName("变异鉴别：把免赔这一步改坏（归零），对拍必须转红")
  void kernelMutationIsDetected() {
    int divergences = 0;
    for (Scenario s : AdjustmentDualTrackFixture.scenarios()) {
      BigDecimal mutated = kernel(s, BigDecimal.ZERO).totalAmount();
      BigDecimal expected = AdjustmentDualTrackFixture.REFERENCE.payout(s);
      if (mutated.compareTo(expected) != 0) {
        divergences++;
      }
    }
    assertThat(divergences).as("免赔归零后至少要在一个场景与 oracle 分歧，否则第三轨没有判别力").isGreaterThan(0);
  }

  @Test
  @DisplayName("缺口哨兵：新路径未偷偷扩大金额语义覆盖（定额 / 津贴仍不支持）")
  void gapSentinelStillHolds() {
    assertThat(AdjustmentCalculationRouter.registeredForms()).hasSize(3);
    assertThat(AdjustmentKernelRegistry.supports(AdjustmentForm.REIMBURSEMENT)).isTrue();
    assertThat(AdjustmentKernelRegistry.supports(AdjustmentForm.FIXED_AMOUNT)).isFalse();
    assertThat(AdjustmentKernelRegistry.supports(AdjustmentForm.ALLOWANCE)).isFalse();
  }

  private static AdjustmentReceipt kernel(Scenario s, BigDecimal deductible) {
    BigDecimal base = BigDecimal.ZERO;
    for (Row r : s.rows()) {
      if (r.headOnly()) {
        continue;
      }
      if (!s.socialOnly() || "IN".equalsIgnoreCase(r.socialFlag())) {
        base = base.add(r.valid());
      }
    }
    AdjustmentCommand command =
        new AdjustmentCommand(
            AdjustmentForm.REIMBURSEMENT, base, deductible, s.payPercent(), s.limit());
    return AdjustmentKernelRegistry.require(AdjustmentForm.REIMBURSEMENT).calculate(command);
  }
}
