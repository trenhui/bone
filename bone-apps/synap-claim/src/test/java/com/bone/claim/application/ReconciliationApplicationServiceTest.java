package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.quota.QuotaAccount;
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

    QuotaAccount balanced = quotaAccount("1000.00", "100.00", "200.00", "700.00"); // 守恒
    QuotaAccount broken =
        quotaAccount("1000.00", "500.00", "600.00", "0.00"); // 500+600+0=1100 ≠ 1000
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

  private static QuotaAccount quotaAccount(
      String initial, String claimed, String frozen, String available) {
    QuotaAccount account = mock(QuotaAccount.class);
    when(account.getQuotaInitial()).thenReturn(new BigDecimal(initial));
    when(account.getQuotaClaimed()).thenReturn(new BigDecimal(claimed));
    when(account.getQuotaFrozen()).thenReturn(new BigDecimal(frozen));
    when(account.available()).thenReturn(new BigDecimal(available));
    return account;
  }
}
