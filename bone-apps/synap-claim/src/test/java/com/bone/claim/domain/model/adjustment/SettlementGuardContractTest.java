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
    return Adjustment.calculate(9001L, TENANT, CLAIM, rows, liability, "r1");
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
  @Disabled(
      "F1 缺口 · EngineFlags#applyAccumulation / applyLiabilityAccumulation：责任/年度限额跨案累计，防同责任多次理算各用满额超赔。"
          + " 生产内核当前无累计快照/占额通道，待 F1 接入 AccumulationPort 后启用。"
          + " 参考 SettlementEngine.java L209-215, L244-251")
  void gap_accumulation_crossCaseLimit() {
    assumeTrue(false, "见 @Disabled 说明：F1 复现后启用");
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
