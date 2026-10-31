package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.ReconciliationReport;
import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.ReconciliationDiffRepository;
import com.bone.claim.domain.repository.ReconciliationReportRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import java.math.BigDecimal;
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

/** 对账应用服务测试：QUOTA 守恒核对 + PAYMENT 结算/流水一致性 + 重跑幂等跳过。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationApplicationServiceTest {

  private static final long TENANT = 7L;
  private static final LocalDate BIZ_DATE = LocalDate.of(2026, 10, 24);

  @Mock private QuotaAccountRepository quotaAccountRepository;
  @Mock private SettlementRepository settlementRepository;
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
            reportRepository,
            diffRepository);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(reportRepository.save(any())).thenReturn(99L);
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
  void skipsWhenReportAlreadyExists() {
    when(reportRepository.findReport(any(), any(), any()))
        .thenReturn(mock(ReconciliationReport.class));

    service.reconcileForTenant(BIZ_DATE);

    verify(reportRepository, never()).save(any());
    verify(diffRepository, never()).save(any());
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
}
