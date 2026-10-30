package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F1 · 理算内核护栏契约测试（防假绿：每条护栏须有"关掉即红"的探针）。
 *
 * <p>生产内核 {@link Adjustment#apply} 已落地子集护栏（基准/免赔/比例/限额截断/socialOnly/零归因/快照可复算/
 * 已确认不可重算/@Version/租户隔离）。本类分两类：
 *
 * <ul>
 *   <li><b>生效测试</b>：断言已落地的护栏确实生效（含本次新增的 {@code clampRatioAt100}）；若被人改坏，必红。
 *   <li><b>@Disabled 缺口测试</b>：登记参考实现 {@code EngineFlags} 中生产内核<b>尚未复现</b>的护栏 （indemnity /
 *       accumulation / idempotency / preauth / apportionment / reversal）。这些护栏依赖
 *       内核当前未接收的输入（第三方已付、累计快照、幂等键、预授权）或未接的累计/占额通道，须随内核接收对应输入（如第三方已付）或接入对应通道（累计/占额/预授权/幂等键）后启用。每个 @Disabled
 *       标注了对应的 {@code EngineFlags} 开关名与参考实现位置，作为可机检的缺口台账。
 * </ul>
 *
 * <p>参考实现（证据）：{@code
 * doc/ai-product/synapClaim/reference-impl/synap-claim-settlement/.../engine/EngineFlags.java} （19
 * 开关）与 {@code SettlementEngine.java}（两轮诊断 N1–N10）。
 */
@DisplayName("F1 · 理算内核护栏契约（SettlementGuardContract）")
class SettlementGuardContractTest {

  private static final long TENANT = 42L;
  private static final long CLAIM = 1001L;

  private static ClaimWithItemsProjection row(
      String name, String socialFlag, BigDecimal valid, BigDecimal selfPay1) {
    return new ClaimWithItemsProjection(
        CLAIM,
        "CL001",
        "P1",
        7L,
        "h",
        "OUTPATIENT",
        "ACCEPTED",
        "SIGNING",
        0,
        "",
        "",
        Instant.now(),
        "self",
        "self",
        new BigDecimal("100.00"),
        null,
        "",
        "",
        "",
        "",
        "v3",
        Instant.now(),
        2001L,
        name,
        "",
        "SERVICE",
        BigDecimal.ONE,
        valid,
        valid,
        socialFlag,
        "",
        BigDecimal.ZERO,
        selfPay1,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        valid,
        BigDecimal.ZERO);
  }

  private static AdjLiability liability(
      String payPercent, String deduct, String limit, Integer socialOnly) {
    return AdjLiability.of(
        1L,
        TENANT,
        "L-1",
        "v1",
        7L,
        "R01",
        "REIMBURSEMENT",
        new BigDecimal(payPercent),
        new BigDecimal(deduct),
        new BigDecimal(limit),
        socialOnly,
        0,
        "ACTIVE");
  }

  private static Adjustment adjust(List<ClaimWithItemsProjection> rows, AdjLiability liability) {
    return adjust(rows, liability, Accumulation.FIRST);
  }

  private static Adjustment adjust(
      List<ClaimWithItemsProjection> rows, AdjLiability liability, Accumulation accumulation) {
    return Adjustment.calculate(9001L, TENANT, CLAIM, rows, liability, "r1", accumulation);
  }

  // ===================== 生效护栏 =====================

  @Test
  @DisplayName("clampRatioAt100：payPercent>100 钳到 100%，防配置超赔（参考 EngineFlags#clampRatioAt100）")
  void clampRatioAt100_neverOverpays() {
    // 基准 200，免赔 0，payPercent=150 → 无钳制会赔 300（超赔）；钳到 100% → 实付 200
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("200.00"), new BigDecimal("200.00"))),
            liability("150", "0", "10000", 1));

    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("200.00");
  }

  // ===================== 缺口护栏（@Disabled，F1 资金路径统一后启用） =====================

  @Test
  @Disabled(
      "F1 缺口 · EngineFlags#applyIndemnityPrinciple：第三方已付（社保统筹/其他保险）先从可赔基数抵扣，防被保险人得利。"
          + " 生产内核当前不接收 thirdPartyPaid 输入，待 F1 资金路径统一后在 Adjustment.apply 复现并启用本探针。"
          + " 参考 SettlementEngine.java L217-219 allocateDeduction(base, reject, ctx.thirdPartyPaid)")
  void gap_indemnityPrinciple_thirdPartyPaidDeducted() {
    assumeTrue(false, "见 @Disabled 说明：F1 复现后启用");
  }

  @Test
  @DisplayName("applyAccumulation（已落地）：责任限额跨案累计 —— 首案用满 1 万后，次案同额费用赔 0，不得各用满额")
  void accumulation_crossCaseLimit() {
    AdjLiability capped = liability("90", "0", "10000", 0);
    List<ClaimWithItemsProjection> rows =
        List.of(row("住院费", "IN", new BigDecimal("20000.00"), new BigDecimal("20000.00")));

    Adjustment first = adjust(rows, capped, Accumulation.FIRST);
    assertThat(first.getTotalAmount())
        .as("首案：20000×90%%=18000，截到责任限额 1 万")
        .isEqualByComparingTo("10000.00");

    // 次案：限额已占用 10000（claimed 或 frozen 都算），剩余 0
    Adjustment second =
        adjust(rows, capped, Accumulation.of(BigDecimal.ZERO, new BigDecimal("10000.00"), true));

    assertThat(second.getTotalAmount())
        .as("次案：责任限额年度已用尽 ⇒ 赔 0。若此处为 10000.00，说明限额仍按配置满额截断（跨案累计失效，系统性超赔）")
        .isEqualByComparingTo("0.00");
    assertThat(second.lines().get(0).getZeroReason())
        .as("零赔付归因须区分『本年度额度已用尽』与『本案单次超限』——拒赔五要素的事实认定要求")
        .isEqualTo("ZERO_LIMIT_EXHAUSTED");
  }

  @Test
  @DisplayName("applyAccumulation（已落地）：年度免赔跨案只扣剩余 —— 首案吃掉 8000 后，次案只再扣 2000，不得重扣全额")
  void accumulation_crossCaseDeductible() {
    AdjLiability withDeduct = liability("90", "10000", "0", 0);
    List<ClaimWithItemsProjection> rows =
        List.of(row("门诊费", "IN", new BigDecimal("8000.00"), new BigDecimal("8000.00")));

    Adjustment first = adjust(rows, withDeduct, Accumulation.FIRST);
    assertThat(first.getTotalAmount()).as("首案 8000 未达 1 万免赔 → 赔 0").isEqualByComparingTo("0.00");
    assertThat(first.getDeductibleApplied())
        .as("首案实际消抵的免赔必须回传 A7：不回写，次案仍会重扣全额")
        .isEqualByComparingTo("8000.00");

    // 次案：本年度已消抵 8000，剩余免赔 2000 → (8000−2000)×90% = 5400
    Adjustment second =
        adjust(rows, withDeduct, Accumulation.of(new BigDecimal("8000.00"), BigDecimal.ZERO, true));

    assertThat(second.getTotalAmount())
        .as("次案须只扣剩余免赔 2000。若此处为 0.00，说明免赔仍按配置全额重扣（跨案累计失效，系统性少赔）")
        .isEqualByComparingTo("5400.00");
    assertThat(second.getDeductibleApplied()).isEqualByComparingTo("2000.00");

    // 第三案：免赔已全部消抵，不再扣
    Adjustment third =
        adjust(
            rows, withDeduct, Accumulation.of(new BigDecimal("10000.00"), BigDecimal.ZERO, true));
    assertThat(third.getTotalAmount()).as("免赔已用尽 → 不再扣减").isEqualByComparingTo("7200.00");
    assertThat(third.getDeductibleApplied()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("累计读侧缺失（available=false）：理算仍出结果但快照标记，不伪造『已用 0』为事实")
  void accumulationMissing_isMarkedNotSilentlyZero() {
    Adjustment adjustment =
        adjust(
            List.of(row("门诊费", "IN", new BigDecimal("8000.00"), new BigDecimal("8000.00"))),
            liability("90", "10000", "0", 0),
            Accumulation.MISSING);

    assertThat(adjustment.isAccumulationAvailable())
        .as("缺账本必须显式标记：A7 据此拒绝占额，若标记恒 true 则资金路径失去 fail-closed 依据")
        .isFalse();
    assertThat(adjustment.getFormulaSnapshot()).contains("\"accumulationAvailable\":false");
  }

  @Test
  @Disabled(
      "F1 缺口 · EngineFlags#applyIdempotency / applyIdempotencyCAS：幂等键返回首次结论、CAS 租约防并发双占额。"
          + " 生产内核当前无幂等键，待 F1 接入后启用。参考 SettlementEngine.java L102-110")
  void gap_idempotency_noDoubleOccupancy() {
    assumeTrue(false, "见 @Disabled 说明：F1 复现后启用");
  }

  @Test
  @Disabled(
      "F1 缺口 · EngineFlags#applyPreAuthFrozen / useRemainingLiability / zeroAuthWhenAllRejected：直付预授权冻结额度与保守授权比例。"
          + " 生产内核当前无预授权通道（直付场景），待 F1 评估是否纳入后启用。参考 SettlementEngine.java L339-414")
  void gap_preAuth_frozenAndConservativeRatio() {
    assumeTrue(false, "见 @Disabled 说明：F1 复现后启用");
  }

  @Test
  @Disabled(
      "F1 缺口 · EngineFlags#apportionRounding（N9 最大余数法配平分位）：逐行取整与总额口径必须守恒，防 1 分对账不平。"
          + " 当前内核为单行汇总，无分位配平需求；若未来扩展多费用行分摊须启用。参考 SettlementEngine.java L462-492")
  void gap_apportionRounding_conservation() {
    assumeTrue(false, "见 @Disabled 说明：F1 复现后启用");
  }

  @Test
  @Disabled(
      "F1 缺口 · EngineFlags#applyReversal（N2/N8 重算/撤件冲正）：旧占用回退，防额度账实不符。待 F1 接入累计通道后启用。"
          + " 参考 SettlementEngine.java L184-191, L326-335")
  void gap_reversal_rollbackOccupancy() {
    assumeTrue(false, "见 @Disabled 说明：F1 复现后启用");
  }
}
