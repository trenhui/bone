package com.bone.claim.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionGraphExecutor;
import com.bone.claim.application.IndemnityApplicationService;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.quota.QuotaAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 跨案累计端到端（⑩ 跨案累计）：同一保单、同一被保人、同一责任的第二案必须"接着上一案算"。
 *
 * <p><b>为什么单案测试测不出这类缺陷</b>：所有既有断言都是单案内的守恒（Σ行=合计、比例/免赔/限额各算一次）， 而"每案都从满额重来"在单案里完全正确。只有把两案串在同一条链路上，
 * 才能区分「读到累计」与「按配置常量重算」。
 *
 * <p><b>本类是全链路判别力的守门人</b>：
 *
 * <ul>
 *   <li>删掉 {@code Accumulation.remainingDeductible} 的减法 ⇒ 次案回到 5400（重扣全额免赔）；
 *   <li>删掉 {@code Accumulation.remainingLimit} 的减法 ⇒ 次案回到 5400（各用满额）；
 *   <li>把适配器改成恒 {@code FIRST} ⇒ 两者同时复发；
 *   <li>删掉 A7 的 {@code reserveLiability} ⇒ 次案回到 5400（账本永远是零占用）。
 * </ul>
 */
@DisplayName("跨案累计端到端（同保单两案）")
class CrossCaseAccumulationScenarioTest {

  private static final String POLICY = "POL-CROSS-001";
  private static final long PLAN = 9101L;
  private static final String INSURED = "ins-cross";

  private ScenarioHarness h;

  @BeforeEach
  void setUp() {
    h = new ScenarioHarness();
    h.seedPolicy(POLICY, PLAN, INSURED);
    h.seedQuotaAccount(INSURED, POLICY, PLAN, new BigDecimal("500000"));

    // 责任：90% 赔付、年度免赔 1 万、责任年限额 1 万（故意设小以同时压中限额截断）
    h.liabRepo.insert(
        AdjLiability.of(
            11L,
            h.tenantId,
            ScenarioHarness.LIABILITY_UUID,
            "v1",
            PLAN,
            "R01",
            "REIMBURSEMENT",
            new BigDecimal("90"),
            new BigDecimal("10000"),
            new BigDecimal("10000"),
            0,
            0,
            "ACTIVE"));
  }

  @Test
  @DisplayName("S5 两案同保单：首案吃掉免赔+用满责任限额后，次案只扣剩余免赔、只赔剩余限额")
  void secondClaimUsesRemainingDeductibleAndLimit() {
    // ---- 第一案：16000 元 ----
    long first = submit(1, new BigDecimal("16000"));
    h.seedEvidence(first, false);
    h.claimSvc.allocate(first, "op-mgr");
    h.claimSvc.startPreExam(first, false, "op-pre");
    h.ruleEngineAvailable = true;
    h.contextPort.set(
        first,
        new com.bone.claim.application.port.out.ClaimContextPort.ClaimContextView(
            first,
            "OUTPATIENT",
            new BigDecimal("16000"),
            true,
            true,
            new BigDecimal("0.95"),
            true));
    h.decisionExecutor.execute(
        first, new DecisionGraphExecutor.DecisionVersions("v1", "v1", "v1", "v1", "v1"));
    h.claimSvc.completePreExam(first, new BigDecimal("16000"), "op-pre");

    IndemnityApplicationService.AdjustmentResult firstAdj =
        h.indemnitySvc.calculate(first, "RULE-1.0.0");
    // (16000 − 10000) × 90% = 5400，未触限额
    assertEquals(0, firstAdj.totalAmount().compareTo(new BigDecimal("5400")), "首案赔付金额");
    assertEquals(0, firstAdj.deductibleApplied().compareTo(new BigDecimal("10000")), "首案消抵全额免赔");
    assertTrue(firstAdj.accumulationAvailable(), "首案读侧应已解析到责任账（seedQuotaAccount 已开两本账）");

    h.setClaimFirstReviewer(first, "op-pre");
    h.autoApproveSvc.approveAndSettle(first, "op-fast");
    h.settleSvc.auditPass(first, "op-audit");
    h.settleSvc.pay(first, "op-pay");

    QuotaAccount liability = liabilityAccount();
    assertNotNull(liability, "责任账必须存在（A7 占额写的就是这一行）");
    assertEquals(
        0, liability.getQuotaClaimed().compareTo(new BigDecimal("5400")), "首案已付计入责任账 claimed");
    assertEquals(0, liability.getDeductibleUsed().compareTo(new BigDecimal("10000")), "首案免赔须回写责任账");

    // ---- 第二案：同一保单、同一责任，同样 16000 元 ----
    long second = submit(2, new BigDecimal("16000"));
    h.seedEvidence(second, false);
    h.claimSvc.allocate(second, "op-mgr");
    h.claimSvc.startPreExam(second, false, "op-pre");
    h.claimSvc.completePreExam(second, new BigDecimal("16000"), "op-pre");

    IndemnityApplicationService.AdjustmentResult secondAdj =
        h.indemnitySvc.calculate(second, "RULE-1.0.0");

    // 剩余免赔 = 10000−10000 = 0 ⇒ 16000×90% = 14400；
    // 剩余限额 = 10000−5400 = 4600 ⇒ 截到 4600。
    // 若跨案累计失效：免赔重扣 → (16000−10000)×90% = 5400；限额满额 → min(14400, 10000) = 10000。
    assertEquals(0, secondAdj.deductibleApplied().compareTo(BigDecimal.ZERO), "次案不得重扣已消抵的免赔");
    assertEquals(
        0,
        secondAdj.totalAmount().compareTo(new BigDecimal("4600")),
        "次案赔付须按『剩余限额』截断。得到 5400 = 免赔没有跨案累计；得到 10000 = 责任限额没有跨案累计");
  }

  @Test
  @DisplayName("S6 撤件回退：首案撤销后免赔与限额都必须归还，次案回到首案口径")
  void releasedClaimRestoresDeductibleAndLimit() {
    long first = submit(1, new BigDecimal("16000"));
    h.seedEvidence(first, false);
    h.claimSvc.allocate(first, "op-mgr");
    h.claimSvc.startPreExam(first, false, "op-pre");
    h.claimSvc.completePreExam(first, new BigDecimal("16000"), "op-pre");
    h.indemnitySvc.calculate(first, "RULE-1.0.0");
    h.setClaimFirstReviewer(first, "op-pre");
    h.autoApproveSvc.approveAndSettle(first, "op-fast");

    assertEquals(
        0, liabilityAccount().getDeductibleUsed().compareTo(new BigDecimal("10000")), "占额时免赔已消抵");

    // 支付前撤件：额度与免赔都应归还
    h.settleSvc.cancel(first, "op-cancel", false);

    QuotaAccount after = liabilityAccount();
    assertEquals(0, after.getQuotaFrozen().compareTo(BigDecimal.ZERO), "撤件须释放责任账预占（否则额度被永久吃掉）");
    assertEquals(
        0, after.getDeductibleUsed().compareTo(BigDecimal.ZERO), "撤件须回退已消抵免赔（否则客户白消化一笔没赔出去的免赔）");

    // 次案回到首案口径：(16000−10000)×90% = 5400
    long second = submit(2, new BigDecimal("16000"));
    h.seedEvidence(second, false);
    h.claimSvc.allocate(second, "op-mgr");
    h.claimSvc.startPreExam(second, false, "op-pre");
    h.claimSvc.completePreExam(second, new BigDecimal("16000"), "op-pre");

    IndemnityApplicationService.AdjustmentResult secondAdj =
        h.indemnitySvc.calculate(second, "RULE-1.0.0");
    assertEquals(
        0, secondAdj.totalAmount().compareTo(new BigDecimal("5400")), "撤件后免赔已归还 ⇒ 次案应重新享有全额免赔");
  }

  // ===================== 助手 =====================

  private QuotaAccount liabilityAccount() {
    return h.quotaRepo.findAccount(
        INSURED,
        POLICY,
        PLAN,
        java.time.Year.now().getValue(),
        "LIABILITY_CAP",
        ScenarioHarness.LIABILITY_UUID);
  }

  private long submit(int seq, BigDecimal amount) {
    List<ScenarioHarness.ItemSeed> items =
        List.of(
            new ScenarioHarness.ItemSeed(
                100L + seq,
                "住院费",
                "A",
                ItemCategory.ofName("SERVICE"),
                BigDecimal.ONE,
                amount,
                amount));
    List<ClaimCaseApplicationService.SubmitClaim.ClaimItemDto> dtos =
        items.stream()
            .map(
                it ->
                    new ClaimCaseApplicationService.SubmitClaim.ClaimItemDto(
                        it.name(),
                        it.code(),
                        it.category(),
                        it.quantity(),
                        it.unitPrice(),
                        it.amount()))
            .toList();
    ClaimCaseApplicationService.SubmitClaim req =
        new ClaimCaseApplicationService.SubmitClaim(
            "idem-cross-" + seq,
            POLICY,
            PLAN,
            INSURED,
            "OUTPATIENT",
            "INV-CROSS-" + seq,
            "NO-CROSS-" + seq,
            Instant.now(),
            "BATCH-1",
            dtos);
    long claimId = h.claimSvc.submit(req).claimId();
    h.seedClaimItems(claimId, items);
    return claimId;
  }
}
