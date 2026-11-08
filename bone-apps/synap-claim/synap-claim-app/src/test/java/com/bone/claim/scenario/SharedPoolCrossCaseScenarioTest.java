package com.bone.claim.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionGraphExecutor;
import com.bone.claim.application.IndemnityApplicationService;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.quota.QuotaAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 共享保额跨案端到端：责任带共保代码时，写侧占额（A7 结算 / 支付）与读侧累计（A6 理算）必须落在 <b>同一行</b> {@code SHARED_POOL} 账户（共保代码为分行键，复用
 * {@code liability_uuid} 列）。
 *
 * <p><b>为什么单案测试测不出这类缺陷</b>：单案只走一遍「先算再占」，写侧记到 A 键、读侧读成 B 键时，首案从满额
 * 起算仍然"看起来正确"；只有把两案串在一条链路上，才能区分「下次理算读到上次共享占用」与「按配置常量重算」。
 *
 * <p><b>本类钉死的缺陷</b>：
 *
 * <ul>
 *   <li>写侧走 {@code SHARED_POOL}、读侧走 {@code LIABILITY_CAP}（读写两本账）⇒ 次案读成零占用、按满额赔 ⇒ 超赔；
 *   <li>共享责任未开户 {@code SHARED_POOL} 而各自开 {@code LIABILITY_CAP} ⇒ 两责任互不看见占用 ⇒ 超赔。
 * </ul>
 */
@DisplayName("共享保额跨案端到端（SHARED_POOL 读写同源）")
class SharedPoolCrossCaseScenarioTest {

  private static final String POLICY = "POL-SH-CROSS-001";
  private static final long PLAN = 9301L;
  private static final String INSURED = "ins-sh-cross";
  private static final String SHARED = "POOL-CROSS";
  private static final String LIABILITY = "LIAB-SH-1";

  private ScenarioHarness h;
  private int year;

  @BeforeEach
  void setUp() {
    h = new ScenarioHarness();
    year = Year.now().getValue();
    h.seedPolicy(POLICY, PLAN, INSURED);
    // 个人账（结算会占）+ 共享池（责任账走 SHARED_POOL）。共享责任刻意不开 LIABILITY_CAP 账户，
    // 以便"读错维度"时返回 MISSING（available=false）而不是一个看起来正常的零占用。
    h.quotaRepo.insert(
        QuotaAccount.open(
            2L, h.tenantId, INSURED, POLICY, PLAN, year, "PERSONAL", "", new BigDecimal("500000")));
    h.quotaRepo.insert(
        QuotaAccount.open(
            4L,
            h.tenantId,
            INSURED,
            POLICY,
            PLAN,
            year,
            "SHARED_POOL",
            SHARED,
            new BigDecimal("10000")));
    // 责任：90% 赔付、无免赔、责任限额 10000，带共保代码（走 SHARED_POOL）。
    h.liabRepo.insert(
        AdjLiability.ofWithShared(
            11L,
            h.tenantId,
            LIABILITY,
            "v1",
            PLAN,
            null,
            "R01",
            "REIMBURSEMENT",
            new BigDecimal("90"),
            BigDecimal.ZERO,
            new BigDecimal("10000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "NONE",
            "NONE",
            "",
            "",
            "NONE",
            "",
            1,
            0,
            SHARED,
            "",
            "ACTIVE"));
  }

  @Test
  @DisplayName("首案结算占共享池 9000 后，次案理算必须读到该占用并按剩余 1000 截断")
  void secondClaimTruncatedBySharedPoolConsumption() {
    // ---- 第一案：10000 元 → 90% = 9000 ----
    long first = submit(1, new BigDecimal("10000"));
    h.seedEvidence(first, false);
    h.claimSvc.allocate(first, "op-mgr");
    h.claimSvc.startPreExam(first, false, "op-pre");
    h.ruleEngineAvailable = true;
    h.contextPort.set(
        first,
        new ClaimContextPort.ClaimContextView(
            first,
            "OUTPATIENT",
            new BigDecimal("10000"),
            true,
            true,
            new BigDecimal("0.95"),
            true));
    h.decisionExecutor.execute(
        first, new DecisionGraphExecutor.DecisionVersions("v1", "v1", "v1", "v1", "v1"));
    h.claimSvc.completePreExam(first, new BigDecimal("10000"), "op-pre");

    IndemnityApplicationService.AdjustmentResult firstAdj =
        h.indemnitySvc.calculate(first, "RULE-1.0.0");
    assertEquals(0, firstAdj.totalAmount().compareTo(new BigDecimal("9000")), "首案赔付 10000×90%");
    assertTrue(firstAdj.accumulationAvailable(), "首案应读到共享池账户（写读同源的前置）");

    h.setClaimFirstReviewer(first, "op-pre");
    h.autoApproveSvc.approveAndSettle(first, "op-fast");
    h.settleSvc.auditPass(first, "op-audit");
    h.settleSvc.pay(first, "op-pay");

    QuotaAccount pool = sharedPool();
    assertNotNull(pool, "共享池账户必须存在（写侧 A7 占额落的就是这一行）");
    assertEquals(0, pool.getQuotaClaimed().compareTo(new BigDecimal("9000")), "首案已付计入共享池 claimed");
    assertNull(liabilityCap(), "共享责任不得各自开 LIABILITY_CAP 账户");

    // ---- 第二案：同一共保代码，同样 10000 元 ----
    long second = submit(2, new BigDecimal("10000"));
    h.seedEvidence(second, false);
    h.claimSvc.allocate(second, "op-mgr");
    h.claimSvc.startPreExam(second, false, "op-pre");
    h.claimSvc.completePreExam(second, new BigDecimal("10000"), "op-pre");

    IndemnityApplicationService.AdjustmentResult secondAdj =
        h.indemnitySvc.calculate(second, "RULE-1.0.0");

    // 剩余限额 = 10000 − 9000 = 1000 ⇒ 9000 截到 1000。
    // 若读侧走错维度（LIABILITY_CAP 未开户 → MISSING）⇒ 不截断、按满额 9000 赔（静默超赔）。
    assertEquals(
        0,
        secondAdj.totalAmount().compareTo(new BigDecimal("1000")),
        "次案赔付须按共享池剩余限额截断；得到 9000 = 读侧未读到共享池占用（读写两本账）");
  }

  // ===================== 助手 =====================

  private QuotaAccount sharedPool() {
    return h.quotaRepo.findAccount(INSURED, POLICY, PLAN, year, "SHARED_POOL", SHARED);
  }

  private QuotaAccount liabilityCap() {
    return h.quotaRepo.findAccount(INSURED, POLICY, PLAN, year, "LIABILITY_CAP", LIABILITY);
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
                        it.amount(),
                        null,
                        ""))
            .toList();
    ClaimCaseApplicationService.SubmitClaim req =
        new ClaimCaseApplicationService.SubmitClaim(
            "idem-sh-" + seq,
            POLICY,
            null,
            PLAN,
            INSURED,
            "OUTPATIENT",
            "INV-SH-" + seq,
            "NO-SH-" + seq,
            null,
            null,
            null,
            Instant.now(),
            "BATCH-1",
            null,
            null,
            dtos);
    long claimId = h.claimSvc.submit(req).claimId();
    h.seedClaimItems(claimId, items);
    return claimId;
  }
}
