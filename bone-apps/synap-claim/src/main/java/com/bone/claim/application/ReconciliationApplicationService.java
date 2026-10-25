package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.reconciliation.DiffType;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.ReconciliationReport;
import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.ReconciliationDiffRepository;
import com.bone.claim.domain.repository.ReconciliationReportRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.core.annotation.NoDomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资金对账应用服务（§11.7，一期内部一致性对账）。
 *
 * <p>由 {@code adapter.schedule} 的 {@code ReconciliationJob} 逐租户驱动（ADR-0030）。一期两条通道：
 *
 * <ul>
 *   <li>{@link ReconciliationReport#CHANNEL_QUOTA}：额度守恒核对，逐账户校验 {@code initial == claimed + frozen
 *       + available}，破坏即记 {@link DiffType#QUOTA_BREAK}；
 *   <li>{@link ReconciliationReport#CHANNEL_PAYMENT}：结算单 vs 资金流水阶段/金额一致性，PAID 结算单须有等额 PAID 流水，非
 *       PAID 结算单末条流水阶段须与结算阶段一致。
 * </ul>
 *
 * <p>外部支付网关/TPA 对账（{@link ReconciliationReport#CHANNEL_TPA}）需外部对账文件接入，二期启用。报告与差异均 append-only，
 * 同日同通道已生成则跳过（重跑幂等）。
 *
 * <p><b>E-5.4 豁免</b>：对账输出为读模型/审计产物，不产生业务领域事件（{@link NoDomainEvent}）。
 */
@Slf4j
@NoDomainEvent
@Service
@RequiredArgsConstructor
public class ReconciliationApplicationService {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private final TenantPort tenantPort;
  private final QuotaAccountRepository quotaAccountRepository;
  private final SettlementRepository settlementRepository;
  private final ReconciliationReportRepository reportRepository;
  private final ReconciliationDiffRepository diffRepository;

  /** 对当前租户某业务日执行 QUOTA + PAYMENT 两通道对账（须在租户上下文内调用）。 */
  @Transactional
  public void reconcileForTenant(LocalDate bizDate) {
    long tenantId = tenantPort.currentTenantId();
    reconcileQuota(tenantId, bizDate);
    reconcilePayment(tenantId, bizDate);
  }

  private void reconcileQuota(long tenantId, LocalDate bizDate) {
    if (reportRepository.findReport(tenantId, bizDate, ReconciliationReport.CHANNEL_QUOTA)
        != null) {
      return; // 幂等：同日同通道已生成
    }
    List<QuotaAccount> accounts = quotaAccountRepository.findAllAccounts();
    int total = accounts.size();
    BigDecimal totalInitial = ZERO;
    BigDecimal totalParts = ZERO;
    List<Violation> violations = new ArrayList<>();
    for (QuotaAccount account : accounts) {
      BigDecimal parts =
          account.getQuotaClaimed().add(account.getQuotaFrozen()).add(account.available());
      totalInitial = totalInitial.add(account.getQuotaInitial());
      totalParts = totalParts.add(parts);
      if (parts.compareTo(account.getQuotaInitial()) != 0) {
        violations.add(
            new Violation(
                "",
                "",
                DiffType.QUOTA_BREAK,
                account.getQuotaInitial(),
                parts,
                "QUOTA",
                "INVARIANT"));
      }
    }
    saveReport(
        tenantId,
        bizDate,
        ReconciliationReport.CHANNEL_QUOTA,
        total,
        violations.size(),
        totalInitial,
        totalParts,
        violations);
  }

  private void reconcilePayment(long tenantId, LocalDate bizDate) {
    if (reportRepository.findReport(tenantId, bizDate, ReconciliationReport.CHANNEL_PAYMENT)
        != null) {
      return;
    }
    List<SettlementOrder> settlements = settlementRepository.findByBizDate(bizDate);
    int total = settlements.size();
    BigDecimal totalAmount = ZERO;
    BigDecimal counterAmount = ZERO;
    List<Violation> violations = new ArrayList<>();
    for (SettlementOrder settlement : settlements) {
      totalAmount = totalAmount.add(settlement.getAmount());
      List<FundingEvent> events =
          settlementRepository.findFundingEventsBySettlementId(settlement.getId());
      if (events.isEmpty()) {
        violations.add(
            new Violation(
                settlement.getClaimNo(),
                settlement.getSettlementNo(),
                DiffType.MISSING_LOCAL,
                settlement.getAmount(),
                null,
                settlement.getFundingStage(),
                "NO_FUNDING_EVENT"));
        continue;
      }
      BigDecimal paidAmount =
          events.stream()
              .filter(e -> "PAID".equals(e.getStage()))
              .map(FundingEvent::getAmount)
              .reduce(ZERO, BigDecimal::add);
      counterAmount = counterAmount.add(paidAmount);

      if ("PAID".equals(settlement.getFundingStage())) {
        if (paidAmount.compareTo(settlement.getAmount()) != 0) {
          violations.add(
              new Violation(
                  settlement.getClaimNo(),
                  settlement.getSettlementNo(),
                  DiffType.AMOUNT_MISMATCH,
                  settlement.getAmount(),
                  paidAmount,
                  settlement.getFundingStage(),
                  "PAID"));
        }
      } else {
        String lastStage = events.get(events.size() - 1).getStage();
        if (!lastStage.equals(settlement.getFundingStage())) {
          violations.add(
              new Violation(
                  settlement.getClaimNo(),
                  settlement.getSettlementNo(),
                  DiffType.STATUS_MISMATCH,
                  settlement.getAmount(),
                  null,
                  settlement.getFundingStage(),
                  lastStage));
        }
      }
    }
    saveReport(
        tenantId,
        bizDate,
        ReconciliationReport.CHANNEL_PAYMENT,
        total,
        violations.size(),
        totalAmount,
        counterAmount,
        violations);
  }

  private void saveReport(
      long tenantId,
      LocalDate bizDate,
      String channel,
      int total,
      int diffCount,
      BigDecimal totalAmount,
      BigDecimal counterAmount,
      List<Violation> violations) {
    ReconciliationReport report =
        ReconciliationReport.create(
            tenantId,
            bizDate,
            channel,
            total,
            total - diffCount,
            diffCount,
            totalAmount,
            counterAmount,
            Instant.now());
    Long reportId = reportRepository.save(report);
    for (Violation v : violations) {
      diffRepository.save(
          ReconciliationDiff.create(
              tenantId,
              reportId,
              v.claimNo(),
              v.settlementNo(),
              v.type(),
              v.localAmount(),
              v.remoteAmount(),
              v.localStage(),
              v.remoteStatus()));
    }
  }

  private record Violation(
      String claimNo,
      String settlementNo,
      DiffType type,
      BigDecimal localAmount,
      BigDecimal remoteAmount,
      String localStage,
      String remoteStatus) {}
}
