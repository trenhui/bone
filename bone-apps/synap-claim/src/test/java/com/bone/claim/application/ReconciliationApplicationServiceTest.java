package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.claim.projection.ClaimSettlementRef;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.ReconciliationReport;
import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.FundingStage;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.ReconciliationDiffRepository;
import com.bone.claim.domain.repository.ReconciliationReportRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 对账应用服务测试：QUOTA 守恒核对 + PAYMENT 结算/流水一致性 + CASE_SETTLE 案件↔结算 + 重跑幂等跳过。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationApplicationServiceTest {

  private static final long TENANT = 7L;
  private static final LocalDate BIZ_DATE = LocalDate.of(2026, 10, 24);

  @Mock private QuotaAccountRepository quotaAccountRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private AdjustmentRepository adjustmentRepository;
  @Mock private ReconciliationReportRepository reportRepository;
  @Mock private ReconciliationDiffRepository diffRepository;
  @Mock private TenantPort tenantPort;

  private ReconciliationApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new ReconciliationApplicationService(
            tenantPort,
            quotaAccountRepository,
            settlementRepository,
            claimCaseRepository,
            adjustmentRepository,
            reportRepository,
            diffRepository);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(reportRepository.save(any())).thenReturn(99L);
    // 默认让 CASE_SETTLE 通道「已存在报告」而跳过：本类多数用例只关心单条通道，
    // 不跳过会让每条用例都多出一份 0 笔的空报告，把 verify(save) 的计数弄乱。
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_CASE_SETTLE))
        .thenReturn(mock(ReconciliationReport.class));
  }

  @Test
  void detectsQuotaBreak() {
    // PAYMENT 通道已存在 → 跳过（只测 QUOTA）
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(null);
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));

    QuotaAccount balanced =
        quotaAccount(1L, "1000.00", "100.00", "200.00", "700.00", "0.00", "100.00");
    // 与账户投影一致的流水（SOFT 200 + PAID 100）⇒ 只命中守恒判据，不产生投影漂移。
    // 流水桩必须先建好再 thenReturn：在 when(...) 之后才创建 mock 会撞 Mockito 的 UnfinishedStubbing。
    List<QuotaTransaction> balancedLedger =
        List.of(txn("SOFT", "OCCUPY", "200.00", "S1"), txn("PAID", "OCCUPY", "100.00", "S2"));
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(balancedLedger);
    QuotaAccount broken =
        quotaAccount(
            2L, "1000.00", "500.00", "600.00", "0.00", "0.00", "500.00"); // 500+600+0=1100 ≠ 1000
    List<QuotaTransaction> brokenLedger =
        List.of(txn("SOFT", "OCCUPY", "600.00", "S1"), txn("PAID", "OCCUPY", "500.00", "S2"));
    when(quotaAccountRepository.findTransactions(2L)).thenReturn(brokenLedger);
    when(quotaAccountRepository.findAllAccounts()).thenReturn(List.of(balanced, broken));

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationReport> reportCaptor =
        ArgumentCaptor.forClass(ReconciliationReport.class);
    verify(reportRepository).save(reportCaptor.capture());
    ReconciliationReport report = reportCaptor.getValue();
    assertEquals(ReconciliationReport.CHANNEL_QUOTA, report.getChannel());
    assertEquals(2, report.getTotalCount());
    assertEquals(1, report.getBalancedCount());
    assertEquals(1, report.getDiffCount());
    assertEquals(ReconciliationReport.STATUS_DIFF, report.getStatus());
    assertEquals(0, report.getTotalAmount().compareTo(new BigDecimal("2000.00")));
    assertEquals(0, report.getCounterAmount().compareTo(new BigDecimal("2100.00")));

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    assertEquals("QUOTA_BREAK", diffCaptor.getValue().getDiffType());
    assertEquals(99L, diffCaptor.getValue().getReportId());
  }

  @Test
  @DisplayName("守恒式平但投影与流水不等 ⇒ PROJECTION_DRIFT（守恒式查不出指针错）")
  void detectsProjectionDriftWhenConservationHolds() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(null);
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));

    // 三值自洽（0+0+20000=20000），但 deductible_used=300 而流水里没有任何 DEDUCTIBLE
    QuotaAccount drifted =
        quotaAccount(1L, "20000.00", "0.00", "0.00", "20000.00", "300.00", "0.00");
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(List.of());
    when(quotaAccountRepository.findAllAccounts()).thenReturn(List.of(drifted));

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationReport> reportCaptor =
        ArgumentCaptor.forClass(ReconciliationReport.class);
    verify(reportRepository).save(reportCaptor.capture());
    assertEquals(0, reportCaptor.getValue().getBalancedCount());
    assertEquals(1, reportCaptor.getValue().getDiffCount());

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    assertEquals("PROJECTION_DRIFT", diffCaptor.getValue().getDiffType());
    // 差异单要能回答"哪一列错了"：列名承载在 remote_status
    assertEquals("deductible_used", diffCaptor.getValue().getRemoteStatus());
    assertEquals(0, diffCaptor.getValue().getLocalAmount().compareTo(new BigDecimal("300.00")));
    assertEquals(0, diffCaptor.getValue().getRemoteAmount().compareTo(new BigDecimal("0.00")));
  }

  @Test
  @DisplayName("一个账户同时命中两条判据 ⇒ 差异两行但只算一个差异账户（balanced + diff = total）")
  void countsDifferingAccountsNotDiffRows() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(null);
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));

    // 守恒被破坏 + 投影与流水完全不符：两条判据都红
    QuotaAccount broken = quotaAccount(1L, "1000.00", "500.00", "600.00", "0.00", "0.00", "500.00");
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(List.of());
    when(quotaAccountRepository.findAllAccounts()).thenReturn(List.of(broken));

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationReport> reportCaptor =
        ArgumentCaptor.forClass(ReconciliationReport.class);
    verify(reportRepository).save(reportCaptor.capture());
    assertEquals(1, reportCaptor.getValue().getTotalCount());
    assertEquals(0, reportCaptor.getValue().getBalancedCount());
    assertEquals(1, reportCaptor.getValue().getDiffCount());
  }

  @Test
  void detectsPaymentAmountMismatch() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(null);

    SettlementOrder settlement = mock(SettlementOrder.class);
    when(settlement.getId()).thenReturn(1L);
    when(settlement.getAmount()).thenReturn(new BigDecimal("500.00"));
    when(settlement.getFundingStage()).thenReturn("PAID");
    when(settlement.getClaimNo()).thenReturn("C1");
    when(settlement.getSettlementNo()).thenReturn("SO1");
    when(settlementRepository.findByBizDate(BIZ_DATE)).thenReturn(List.of(settlement));

    FundingEvent paid = mock(FundingEvent.class);
    when(paid.getStage()).thenReturn("PAID");
    when(paid.getAmount()).thenReturn(new BigDecimal("400.00"));
    when(settlementRepository.findFundingEventsBySettlementId(1L)).thenReturn(List.of(paid));

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationReport> reportCaptor =
        ArgumentCaptor.forClass(ReconciliationReport.class);
    verify(reportRepository).save(reportCaptor.capture());
    ReconciliationReport report = reportCaptor.getValue();
    assertEquals(ReconciliationReport.CHANNEL_PAYMENT, report.getChannel());
    assertEquals(1, report.getDiffCount());
    assertEquals(ReconciliationReport.STATUS_DIFF, report.getStatus());

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    assertEquals("AMOUNT_MISMATCH", diffCaptor.getValue().getDiffType());
    assertEquals(0, diffCaptor.getValue().getLocalAmount().compareTo(new BigDecimal("500.00")));
    assertEquals(0, diffCaptor.getValue().getRemoteAmount().compareTo(new BigDecimal("400.00")));
  }

  @Test
  @DisplayName("流水 PAID 后又出现 SOFT_RESERVED ⇒ STAGE_REGRESSION（末条比对看不出倒拨）")
  void detectsStageRegression() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(null);

    SettlementOrder settlement = settlement(1L, 21L, "C21", "SO21", "800.00", FundingStage.PAID);
    when(settlementRepository.findByBizDate(BIZ_DATE)).thenReturn(List.of(settlement));
    List<FundingEvent> events =
        List.of(
            event("SOFT_RESERVED", 0),
            event("HARD_RESERVED", 1),
            event("PAID", 2),
            event("SOFT_RESERVED", 3));
    when(settlementRepository.findFundingEventsBySettlementId(1L)).thenReturn(events);

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    // 只出一条差异：结算单阶段仍是 PAID、PAID 流水金额也等额，判据 ①② 全过——这正是「末条比对看不出倒拨」
    verify(diffRepository).save(diffCaptor.capture());
    ReconciliationDiff regression = diffCaptor.getValue();
    assertEquals("STAGE_REGRESSION", regression.getDiffType());
    assertEquals("PAID", regression.getLocalStage());
    assertEquals("SOFT_RESERVED", regression.getRemoteStatus());
  }

  @Test
  @DisplayName("结案但一张结算单都没有 ⇒ MISSING_LOCAL（结案侧扫不到就没有人会发现）")
  void detectsFinishedClaimWithoutSettlement() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_CASE_SETTLE))
        .thenReturn(null);
    ClaimSettlementRef finished = new ClaimSettlementRef(11L, "C11", new BigDecimal("800.00"));
    Adjustment adjust = adjustment("800.00");
    when(claimCaseRepository.findFinishedBetween(any(), any())).thenReturn(List.of(finished));
    when(settlementRepository.findByBizDate(BIZ_DATE)).thenReturn(List.of());
    when(settlementRepository.findByClaimId(11L)).thenReturn(List.of());
    when(adjustmentRepository.findLatestByClaimId(11L)).thenReturn(adjust);

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationReport> reportCaptor =
        ArgumentCaptor.forClass(ReconciliationReport.class);
    verify(reportRepository).save(reportCaptor.capture());
    ReconciliationReport report = reportCaptor.getValue();
    assertEquals(ReconciliationReport.CHANNEL_CASE_SETTLE, report.getChannel());
    assertEquals(1, report.getTotalCount());
    assertEquals(0, report.getBalancedCount());
    assertEquals(1, report.getDiffCount());
    assertEquals(0, report.getTotalAmount().compareTo(new BigDecimal("0.00")));
    assertEquals(0, report.getCounterAmount().compareTo(new BigDecimal("800.00")));

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    assertEquals("MISSING_LOCAL", diffCaptor.getValue().getDiffType());
    assertEquals("NO_SETTLEMENT", diffCaptor.getValue().getRemoteStatus());
    assertEquals(0, diffCaptor.getValue().getRemoteAmount().compareTo(new BigDecimal("800.00")));
  }

  @Test
  @DisplayName("净已付 ≠ 最新理算总额 ⇒ CASE_SETTLE_MISMATCH（判决基准是理算，不是核定快照）")
  void detectsCaseSettleAmountMismatch() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_CASE_SETTLE))
        .thenReturn(null);
    when(claimCaseRepository.findFinishedBetween(any(), any())).thenReturn(List.of());

    SettlementOrder settlement = settlement(2L, 12L, "C12", "SO12", "700.00", FundingStage.PAID);
    Adjustment adjust = adjustment("800.00");
    when(settlementRepository.findByBizDate(BIZ_DATE)).thenReturn(List.of(settlement));
    when(settlementRepository.findByClaimId(12L)).thenReturn(List.of(settlement));
    when(adjustmentRepository.findLatestByClaimId(12L)).thenReturn(adjust);

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    assertEquals("CASE_SETTLE_MISMATCH", diffCaptor.getValue().getDiffType());
    assertEquals(0, diffCaptor.getValue().getLocalAmount().compareTo(new BigDecimal("700.00")));
    assertEquals(0, diffCaptor.getValue().getRemoteAmount().compareTo(new BigDecimal("800.00")));
  }

  @Test
  @DisplayName("有在途结算单 ⇒ CASE_SETTLE 不判（钱还没走完，此刻净付当然不等于应付）")
  void skipsCaseSettleWhenSettlementInFlight() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_CASE_SETTLE))
        .thenReturn(null);
    when(claimCaseRepository.findFinishedBetween(any(), any())).thenReturn(List.of());

    SettlementOrder settlement =
        settlement(3L, 13L, "C13", "SO13", "700.00", FundingStage.SOFT_RESERVED);
    Adjustment adjust = adjustment("800.00");
    when(settlementRepository.findByBizDate(BIZ_DATE)).thenReturn(List.of(settlement));
    when(settlementRepository.findByClaimId(13L)).thenReturn(List.of(settlement));
    when(adjustmentRepository.findLatestByClaimId(13L)).thenReturn(adjust);

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationReport> reportCaptor =
        ArgumentCaptor.forClass(ReconciliationReport.class);
    verify(reportRepository).save(reportCaptor.capture());
    assertEquals(0, reportCaptor.getValue().getDiffCount());
    assertEquals(ReconciliationReport.STATUS_BALANCED, reportCaptor.getValue().getStatus());
    verify(diffRepository, never()).save(any());
  }

  @Test
  @DisplayName("两笔流水从同一个旧值出发 ⇒ LEDGER_CHAIN_BREAK（投影照样等于 Σ流水，投影判据看不见）")
  void detectsLedgerChainBreak() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(null);
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(mock(ReconciliationReport.class));

    // 账户投影与 Σ流水一致（冻结 500 = 300 + 200），守恒也平：只有"第二笔又从 0 起"这件事是错的
    QuotaAccount account =
        quotaAccount(1L, "20000.00", "0.00", "500.00", "19500.00", "0.00", "0.00");
    List<QuotaTransaction> ledger =
        List.of(
            chainTxn("SOFT", "OCCUPY", "300.00", "0.00", "300.00", "S1", "K1"),
            chainTxn("SOFT", "OCCUPY", "200.00", "0.00", "200.00", "S2", "K2"));
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(ledger);
    when(quotaAccountRepository.findAllAccounts()).thenReturn(List.of(account));

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    ReconciliationDiff diff = diffCaptor.getValue();
    assertEquals("LEDGER_CHAIN_BREAK", diff.getDiffType());
    // 差异单要能回答"哪一列、哪种断法"
    assertEquals("quota_frozen:CONTINUITY", diff.getRemoteStatus());
    assertEquals(0, diff.getLocalAmount().compareTo(new BigDecimal("0.00")));
    assertEquals(0, diff.getRemoteAmount().compareTo(new BigDecimal("300.00")));
  }

  @Test
  @DisplayName("大额已付款却从未进复核冻结 ⇒ REVIEW_EVIDENCE_MISSING（流水逐条合法，缺的是「谁点的」）")
  void detectsReviewEvidenceGap() {
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_QUOTA))
        .thenReturn(mock(ReconciliationReport.class));
    when(reportRepository.findReport(TENANT, BIZ_DATE, ReconciliationReport.CHANNEL_PAYMENT))
        .thenReturn(null);

    SettlementOrder settlement = settlement(5L, 25L, "C25", "SO25", "20000.00", FundingStage.PAID);
    when(settlementRepository.findByBizDate(BIZ_DATE)).thenReturn(List.of(settlement));
    List<FundingEvent> events =
        List.of(
            eventAmount("SOFT_RESERVED", 0, "20000.00"),
            eventAmount("HARD_RESERVED", 1, "20000.00"),
            eventAmount("PAID", 2, "20000.00"));
    when(settlementRepository.findFundingEventsBySettlementId(5L)).thenReturn(events);

    service.reconcileForTenant(BIZ_DATE);

    ArgumentCaptor<ReconciliationDiff> diffCaptor =
        ArgumentCaptor.forClass(ReconciliationDiff.class);
    verify(diffRepository).save(diffCaptor.capture());
    ReconciliationDiff diff = diffCaptor.getValue();
    assertEquals("REVIEW_EVIDENCE_MISSING", diff.getDiffType());
    assertEquals("MISSING_REVIEW_EVENT", diff.getRemoteStatus());
  }

  @Test
  void skipsWhenReportAlreadyExists() {
    when(reportRepository.findReport(any(), any(), any()))
        .thenReturn(mock(ReconciliationReport.class));

    service.reconcileForTenant(BIZ_DATE);

    verify(reportRepository, never()).save(any());
    verify(diffRepository, never()).save(any());
  }

  /** 结算单桩（金额/阶段/归属都给出：CASE_SETTLE 判据依赖 claimId 与 fundingStageEnum）。 */
  private static SettlementOrder settlement(
      long id,
      long claimId,
      String claimNo,
      String settlementNo,
      String amount,
      FundingStage stage) {
    SettlementOrder order = mock(SettlementOrder.class);
    when(order.getId()).thenReturn(id);
    when(order.getClaimId()).thenReturn(claimId);
    when(order.getClaimNo()).thenReturn(claimNo);
    when(order.getSettlementNo()).thenReturn(settlementNo);
    when(order.getAmount()).thenReturn(new BigDecimal(amount));
    when(order.getFundingStage()).thenReturn(stage.name());
    when(order.fundingStageEnum()).thenReturn(stage);
    return order;
  }

  /** 资金流水桩（{@code offsetSeconds} 决定事件先后，阶段链校验按顺序判）。 */
  private static FundingEvent event(String stage, long offsetSeconds) {
    FundingEvent e = mock(FundingEvent.class);
    when(e.getStage()).thenReturn(stage);
    when(e.stageEnum()).thenReturn(FundingStage.valueOf(stage));
    when(e.getAmount()).thenReturn(new BigDecimal("800.00"));
    when(e.getEventTime())
        .thenReturn(Instant.parse("2026-10-24T01:00:00Z").plusSeconds(offsetSeconds));
    return e;
  }

  private static Adjustment adjustment(String totalAmount) {
    Adjustment adjustment = mock(Adjustment.class);
    when(adjustment.getTotalAmount()).thenReturn(new BigDecimal(totalAmount));
    return adjustment;
  }

  /** 账户桩（{@code deductibleUsed} 与 {@code capUsed} 显式给出：对账要逐列比对投影， 这两列若留空会被当成"投影 0"而平白多出漂移）。 */
  private static QuotaAccount quotaAccount(
      long id,
      String initial,
      String claimed,
      String frozen,
      String available,
      String deductibleUsed,
      String capUsed) {
    QuotaAccount account = mock(QuotaAccount.class);
    when(account.getId()).thenReturn(id);
    when(account.getQuotaInitial()).thenReturn(new BigDecimal(initial));
    when(account.getQuotaClaimed()).thenReturn(new BigDecimal(claimed));
    when(account.getQuotaFrozen()).thenReturn(new BigDecimal(frozen));
    when(account.available()).thenReturn(new BigDecimal(available));
    when(account.getDeductibleUsed()).thenReturn(new BigDecimal(deductibleUsed));
    when(account.getCapUsed()).thenReturn(new BigDecimal(capUsed));
    return account;
  }

  /** 流水桩（投影重算只读方向/类型/金额/结算单号）。 */
  private static QuotaTransaction txn(
      String txnType, String direction, String amount, String settlementNo) {
    QuotaTransaction t = mock(QuotaTransaction.class);
    when(t.getTxnType()).thenReturn(txnType);
    when(t.getDirection()).thenReturn(direction);
    when(t.getAmount()).thenReturn(new BigDecimal(amount));
    when(t.getSettlementNo()).thenReturn(settlementNo);
    return t;
  }

  /** 带 before/after 的流水桩（串链判据要读前后值；投影判据不读，故只在串链用例里给）。 */
  private static QuotaTransaction chainTxn(
      String txnType,
      String direction,
      String amount,
      String before,
      String after,
      String settlementNo,
      String idempotencyKey) {
    QuotaTransaction t = txn(txnType, direction, amount, settlementNo);
    when(t.getBeforeAmount()).thenReturn(new BigDecimal(before));
    when(t.getAfterAmount()).thenReturn(new BigDecimal(after));
    when(t.getIdempotencyKey()).thenReturn(idempotencyKey);
    return t;
  }

  /** 带金额的资金流水桩（复核留痕判据按大额门判，金额必须真的够大）。 */
  private static FundingEvent eventAmount(String stage, long offsetSeconds, String amount) {
    FundingEvent e = event(stage, offsetSeconds);
    when(e.getAmount()).thenReturn(new BigDecimal(amount));
    return e;
  }
}
