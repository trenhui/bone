package com.bone.claim.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 额度释放的<b>归属校验</b>与<b>账实一致性</b>（资金 P0，2026-10-31）。
 *
 * <p><b>为什么单案测试测不出这类缺陷</b>：单案的释放永远"池里正好有我那笔钱"，按池余额释放与按自有
 * 预占释放<b>结果完全一样</b>。只有把两个结算单放进<b>同一本共享账</b>（责任账按 {@code liability_uuid}
 * 分行，同一责任的多案共用），才能区分"释放自己的钱"与"释放共享池里别人的钱"。
 *
 * <p><b>本类是全链路判别力的守门人</b>：
 *
 * <ul>
 *   <li>把 {@code releaseOwned} 换回按池余额的 {@code release} ⇒ T1 转红（B 单的预占被 A 单吃掉）；
 *   <li>去掉"自有预占不足即拒"⇒ T2 转红（A 单挪用了 B 单的预占）；
 *   <li>去掉免赔的归属校验 ⇒ T3 转红（回退了别人已消抵的免赔）。
 * </ul>
 *
 * <p>缺陷原型（生产库实证）：claim {@code 770803272792408064}（建于责任账接线之前）在责任账上 <b>只有 RELEASE 24.30、没有 SOFT</b>
 * —— 一笔从未占过责任账的结算单，从共享池里放走了一笔钱， 抽干后他人合法释放被拒、结算单永久卡死。
 */
@DisplayName("额度释放归属与账实一致性（资金 P0）")
class QuotaReleaseOwnershipScenarioTest {

  private static final String POLICY = "POL-REL-001";
  private static final long PLAN = 9201L;
  private static final String INSURED = "ins-rel";

  /** 索赔金额 16000：责任赔付比例 90%、年免赔 10000 ⇒ 赔付额 = (16000−10000)×90% = 5400。 */
  private static final BigDecimal CLAIM_AMOUNT = new BigDecimal("16000.00");

  private static final BigDecimal PAYOUT = new BigDecimal("5400.00");
  private static final BigDecimal DEDUCTIBLE = new BigDecimal("10000.00");

  private ScenarioHarness h;

  @BeforeEach
  void setUp() {
    h = new ScenarioHarness();
    h.seedPolicy(POLICY, PLAN, INSURED);
    h.seedQuotaAccount(INSURED, POLICY, PLAN, new BigDecimal("500000"));

    // 责任配置：赔付比例 90%、年免赔 1 万、年限额 10 万。
    // 免赔非零是必要的：只有存在"他人已消抵的免赔"，才能验证取消本单不会把它退回来。
    h.liabRepo.insert(
        AdjLiability.of(
            21L,
            h.tenantId,
            ScenarioHarness.LIABILITY_UUID,
            "v1",
            PLAN,
            "R01",
            "REIMBURSEMENT",
            new BigDecimal("90"),
            DEDUCTIBLE,
            new BigDecimal("100000"),
            1,
            0,
            "ACTIVE"));
  }

  @Test
  @DisplayName("T1 越权释放被拒：A 单在责任账无自有预占，释放必须是空操作，不得吃掉 B 单的预占")
  void releaseWithoutOwnHoldMustNotStealOthersHold() {
    // ---- B 单：正常建结算单（两本账都占额），把责任账的共享池压住 ----
    long claimB = settledClaim(1);
    QuotaAccount liability = liabilityAccount();
    BigDecimal frozenBefore = liability.getQuotaFrozen();
    assertTrue(frozenBefore.compareTo(BigDecimal.ZERO) > 0, "B 单建结算单后应已占住责任账预占，实际 " + frozenBefore);

    // ---- A 单：模拟"建于责任账接线之前"的历史结算单：只在个人账占额，责任账从无预占 ----
    long claimA = submit(2, CLAIM_AMOUNT);
    String settlementNoA = "SO" + claimA + "-1";
    // 旧版 create() 只占个人账（责任账接线之前上线），此处精确复刻该形态
    h.quotaSvc.reserve(
        h.tenantId, INSURED, POLICY, PLAN, claimA, claimNo(claimA), PAYOUT, settlementNoA, 1);
    // 责任账上 A 单的流水为空 ⇒ 其自有冻结额为 0
    assertEquals(
        0,
        liabilityAccount().getTransactions().stream()
            .filter(t -> settlementNoA.equals(t.getSettlementNo()))
            .count(),
        "前置条件：A 单在责任账上不得有任何流水");

    // 历史 cancel() 走的就是这条路径：从责任账释放 A 单的金额
    h.quotaSvc.releaseLiability(
        h.tenantId,
        INSURED,
        POLICY,
        PLAN,
        Year.now().getValue(),
        ScenarioHarness.LIABILITY_UUID,
        claimA,
        claimNo(claimA),
        PAYOUT,
        BigDecimal.ZERO,
        settlementNoA,
        1);

    QuotaAccount after = liabilityAccount();
    assertEquals(
        0,
        after.getQuotaFrozen().compareTo(frozenBefore),
        "A 单在责任账无自有预占 ⇒ 释放必须空操作，绝不能吃掉 B 单的预占（越权释放）");
    assertEquals(
        0,
        after.getTransactions().stream()
            .filter(t -> settlementNoA.equals(t.getSettlementNo()))
            .count(),
        "空操作不得为 A 单落下任何责任账流水");
  }

  @Test
  @DisplayName("T2 账实不符必须 fail-closed：本单自有预占不足时拒绝释放，不得挪用他人预占")
  void partialOwnHoldMustFailClosed() {
    long claimB = settledClaim(1); // B 占住责任账
    long claimA = settledClaim(2); // A 再占一笔（共享池里两笔并存）
    String settlementNoA = "SO" + claimA + "-1";
    QuotaAccount liability = liabilityAccount();

    // A 的自有预占 = 该结算单的实际占额（从结算单读，不硬编码：次案的免赔已被首案吃掉，金额与首案不同）
    BigDecimal aOwn = h.settleSvc.latest(claimA).amount();
    BigDecimal frozenBoth = liability.getQuotaFrozen();
    assertTrue(
        frozenBoth.compareTo(aOwn) > 0,
        "前置条件：共享池里必须还有 B 单的预占（合计 " + frozenBoth + " > A 单的 " + aOwn + "）");

    // 模拟历史上 A 单已释放过一部分：本单自有预占只剩 100
    liability.release(
        9001L, aOwn.subtract(new BigDecimal("100.00")), claimA, settlementNoA, "LEGACY:PARTIAL:1");
    BigDecimal frozenBefore = liability.getQuotaFrozen();
    assertEquals(
        0,
        frozenBefore.compareTo(frozenBoth.subtract(aOwn).add(new BigDecimal("100.00"))),
        "前置条件：本单自有预占只剩 100");

    DomainException thrown =
        assertThrows(
            DomainException.class,
            () ->
                h.quotaSvc.releaseLiability(
                    h.tenantId,
                    INSURED,
                    POLICY,
                    PLAN,
                    Year.now().getValue(),
                    ScenarioHarness.LIABILITY_UUID,
                    claimA,
                    claimNo(claimA),
                    aOwn,
                    BigDecimal.ZERO,
                    settlementNoA,
                    1),
            "自有预占不足时必须拒绝释放（fail-closed）：若按共享池余额放行，会挪用 B 单的预占");
    assertTrue(thrown.getMessage().contains("拒绝越权释放"), "报错须指向越权释放，实际：" + thrown.getMessage());
    assertEquals(0, liabilityAccount().getQuotaFrozen().compareTo(frozenBefore), "拒绝后共享池冻结额不得被改动");
  }

  @Test
  @DisplayName("T3 正常取消回归：两本账的预占都归还、只回退本单消抵的免赔，不得回退他人的")
  void normalCancelRestoresBothBooks() {
    // A 是首案：吃掉整笔年免赔 10000
    long claimA = settledClaim(1);
    String settlementNoA = "SO" + claimA + "-1";

    assertEquals(
        0, liabilityAccount().getQuotaFrozen().compareTo(PAYOUT), "前置条件：A 单已占住责任账（自有预占 = 赔付额）");
    assertEquals(0, personalAccount().getQuotaFrozen().compareTo(PAYOUT), "前置条件：A 单已占住个人账");
    assertEquals(
        0, liabilityAccount().getDeductibleUsed().compareTo(DEDUCTIBLE), "前置条件：A 单已消抵年免赔 10000");

    // 别的结算单消抵的免赔（共享池）：A 单取消时不得把它退回来
    liabilityAccount()
        .accumulateDeductible(
            9002L, new BigDecimal("4000.00"), 999999L, "SO-OTHER-1", "OTHER:DED:1");

    h.settleSvc.cancel(claimA, "op-cancel", false);

    assertEquals(
        0, liabilityAccount().getQuotaFrozen().compareTo(BigDecimal.ZERO), "取消须归还本单在责任账的自有预占");
    assertEquals(
        0, personalAccount().getQuotaFrozen().compareTo(BigDecimal.ZERO), "取消须归还本单在个人账的预占");
    assertEquals(
        0,
        liabilityAccount().getDeductibleUsed().compareTo(new BigDecimal("4000.00")),
        "只回退本单消抵的免赔 10000；他人已消抵的 4000 必须原样保留（否则客户白消化一笔没赔出去的免赔）");
  }

  @Test
  @DisplayName("T3b 免赔归属必须 fail-closed：本单自有免赔不足时拒绝回退，不得吃掉他人已消抵的免赔")
  void deductibleOwnershipMustFailClosed() {
    // A 首案：消抵年免赔 10000
    long claimA = settledClaim(1);
    String settlementNoA = "SO" + claimA + "-1";

    // 模拟历史上 A 单的免赔已被部分回退：本单自有免赔 10000 → 4000
    liabilityAccount()
        .reduceDeductible(
            9003L, new BigDecimal("6000.00"), claimA, settlementNoA, "LEGACY:PARTIAL_DED:1");
    // 别的结算单消抵的免赔（共享池）
    liabilityAccount()
        .accumulateDeductible(
            9002L, new BigDecimal("4000.00"), 999999L, "SO-OTHER-1", "OTHER:DED:1");

    BigDecimal dedBefore = liabilityAccount().getDeductibleUsed();
    assertEquals(
        0, dedBefore.compareTo(new BigDecimal("8000.00")), "前置条件：本单 4000 + 他人 4000 = 8000");

    DomainException thrown =
        assertThrows(
            DomainException.class,
            () -> h.settleSvc.cancel(claimA, "op-cancel", false),
            "本单自有免赔消抵不足时必须拒绝回退：若按共享池余额放行，会吃掉他人已消抵的 4000 免赔");
    assertTrue(thrown.getMessage().contains("拒绝越权回退"), "报错须指向越权回退，实际：" + thrown.getMessage());
    assertEquals(
        0,
        liabilityAccount().getDeductibleUsed().compareTo(dedBefore),
        "拒绝后共享池免赔不得被改动（他人已消抵的 4000 必须原样保留）");
  }

  // ===================== F3：池投影被抽干不得阻塞本单释放（投影自愈） =====================

  @Test
  @DisplayName("T4 共享池投影被抽干时，本单自有 hold 足额 ⇒ 必须自愈并正常释放，不得被池级余额永久拒掉")
  void deficitProjectionMustSelfHealAndRelease() {
    long claimB = settledClaim(1);
    String settlementNoB = "SO" + claimB + "-1";
    BigDecimal payout = h.settleSvc.latest(claimB).amount();
    assertEquals(
        0, liabilityAccount().getQuotaFrozen().compareTo(payout), "前置条件：B 单已占住责任账（自有预占 = 赔付额）");

    // 模拟历史越权释放把共享池抽干：账户投影 frozen 被扣成 0，而流水事实（B 单的 SOFT）仍在
    QuotaAccount liability = liabilityAccount();
    liability.reconcileProjection(
        BigDecimal.ZERO, liability.getQuotaClaimed(), liability.getDeductibleUsed());
    assertEquals(
        0, liabilityAccount().getQuotaFrozen().compareTo(BigDecimal.ZERO), "前置条件：共享池投影已被抽干");

    // 取消本单：自有 hold 足额 ⇒ 释放必须成功（应用层按流水自愈重算投影）
    h.settleSvc.cancel(claimB, "op-cancel", false);

    QuotaAccount after = liabilityAccount();
    assertEquals(0, after.getQuotaFrozen().compareTo(BigDecimal.ZERO), "释放后本单在责任账的占用必须归零");
    assertTrue(
        after.getTransactions().stream()
            .anyMatch(
                t -> settlementNoB.equals(t.getSettlementNo()) && "RELEASE".equals(t.getTxnType())),
        "必须落下本单的 RELEASE 流水（投影自愈后完成释放）");
  }

  @Test
  @DisplayName("T4b 自愈重算必须保留他人占用：重算后共享池冻结额 = 其余各单净占用之和")
  void selfHealMustNotDropOthersHold() {
    long claimA = settledClaim(1);
    long claimB = settledClaim(2); // 次案（年免赔已被首案吃掉，占额与首案不同）
    BigDecimal aOwn = h.settleSvc.latest(claimA).amount();
    BigDecimal bOwn = h.settleSvc.latest(claimB).amount();
    assertEquals(
        0, liabilityAccount().getQuotaFrozen().compareTo(aOwn.add(bOwn)), "前置条件：两单合计占住责任账");

    // 抽干共享池投影（流水事实仍在）
    QuotaAccount liability = liabilityAccount();
    liability.reconcileProjection(
        BigDecimal.ZERO, liability.getQuotaClaimed(), liability.getDeductibleUsed());

    // 取消 A 单 ⇒ 自愈重算后，B 单的占用必须原样保留
    h.settleSvc.cancel(claimA, "op-cancel", false);

    assertEquals(
        0,
        liabilityAccount().getQuotaFrozen().compareTo(bOwn),
        "自愈重算必须保留他人（B 单）的占用：若重算只认本单，B 单的额度会被凭空抹掉（可赔额度被放大）");
  }

  // ===================== 助手 =====================

  private QuotaAccount liabilityAccount() {
    return h.quotaRepo.findAccount(
        INSURED,
        POLICY,
        PLAN,
        Year.now().getValue(),
        "LIABILITY_CAP",
        ScenarioHarness.LIABILITY_UUID);
  }

  private QuotaAccount personalAccount() {
    return h.quotaRepo.findAccount(INSURED, POLICY, PLAN, Year.now().getValue(), "PERSONAL", "");
  }

  private String claimNo(long claimId) {
    return h.claimSvc.getClaimWithItems(claimId).get(0).getClaimNo();
  }

  /** 走完"报案→核赔→建结算单"，返回 claimId（结算单号固定为 {@code SO{claimId}-1}）。 */
  private long settledClaim(int seq) {
    long claimId = submit(seq, CLAIM_AMOUNT);
    h.seedEvidence(claimId, false);
    h.claimSvc.allocate(claimId, "op-mgr");
    h.claimSvc.startPreExam(claimId, false, "op-pre");
    h.claimSvc.completePreExam(claimId, CLAIM_AMOUNT, "op-pre");
    h.indemnitySvc.calculate(claimId, "RULE-1.0.0");
    h.setClaimFirstReviewer(claimId, "op-pre");
    h.autoApproveSvc.approveAndSettle(claimId, "op-fast");
    return claimId;
  }

  private long submit(int seq, BigDecimal amount) {
    List<ScenarioHarness.ItemSeed> items =
        List.of(
            new ScenarioHarness.ItemSeed(
                200L + seq,
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
                        it.amount(),
                        null))
            .toList();
    ClaimCaseApplicationService.SubmitClaim req =
        new ClaimCaseApplicationService.SubmitClaim(
            "idem-rel-" + seq,
            POLICY,
            PLAN,
            INSURED,
            "OUTPATIENT",
            "INV-REL-" + seq,
            "NO-REL-" + seq,
            null,
            null,
            null,
            Instant.now(),
            "BATCH-REL",
            dtos);
    long claimId = h.claimSvc.submit(req).claimId();
    h.seedClaimItems(claimId, items);
    return claimId;
  }
}
