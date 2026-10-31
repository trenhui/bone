package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaProjection;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
 *   <li>{@link ReconciliationReport#CHANNEL_QUOTA}：额度账户两条判据——①守恒式 {@code initial == claimed +
 *       frozen + available}，破坏记 {@link DiffType#QUOTA_BREAK}；②<b>投影 = 事实</b>：按 {@code
 *       t_quota_transaction} 重算 {@code frozen/claimed/deductible_used/cap_used} 并与账户行比对，不等记 {@link
 *       DiffType#PROJECTION_DRIFT}（§11.7 不变量 1 ①）。
 *   <li>{@link ReconciliationReport#CHANNEL_PAYMENT}：结算单 vs 资金流水阶段/金额一致性，PAID 结算单须有等额 PAID 流水，非
 *       PAID 结算单末条流水阶段须与结算阶段一致。
 * </ul>
 *
 * <p><b>为什么两条判据都要</b>：守恒式只校验账户内部三值自洽，一根指针错到底（{@code claimed} 少记一笔、 {@code deductible_used}
 * 多记一次）照样"自洽"；反过来，投影漂移也不一定破坏守恒（{@code claimed} 与 {@code frozen} 一增一减）。只查前者时，错的值会被当成"平账"放过去——这正是
 * §11.7 不变量 1 把 "账户 = Σ流水"和"三值自洽"并列的原因。
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
    // 差异按<b>账户</b>计一份（一个账户可同时命中守恒与投影两条判据，差异明细会是两行）：
    // 报告里的 balanced/diff 是账户口径，若直接用差异行数当 diffCount，会出现 balanced + diff > total。
    // 用身份集合而不是 id：账户可能尚未落库（id 为空），身份去重不会把两个值相同但互不相关的账户合并。
    Set<QuotaAccount> differingAccounts = Collections.newSetFromMap(new IdentityHashMap<>());
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
        differingAccounts.add(account);
      }
      // 判据 ②：账户是投影，流水是事实（§11.7 不变量 1 ①）。重算口径与写侧自愈共用
      // QuotaProjection#rebuild——两处若各写一遍，就会出现"自愈按 A 口径修、对账按 B 口径判"。
      List<QuotaProjection.Drift> drifts =
          QuotaProjection.rebuild(quotaAccountRepository.findTransactions(account.getId()))
              .driftAgainst(account);
      for (QuotaProjection.Drift drift : drifts) {
        // 差异单无"列名"字段，用 remote_status 承载漂移列名（"哪一列错了"是派单的第一条信息）。
        violations.add(
            new Violation(
                "",
                "",
                DiffType.PROJECTION_DRIFT,
                drift.projection(),
                drift.ledger(),
                "QUOTA",
                drift.field()));
        differingAccounts.add(account);
      }
    }
    saveReport(
        tenantId,
        bizDate,
        ReconciliationReport.CHANNEL_QUOTA,
        total,
        differingAccounts.size(),
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
      int differingAccounts,
      BigDecimal totalAmount,
      BigDecimal counterAmount,
      List<Violation> violations) {
    ReconciliationReport report =
        ReconciliationReport.create(
            tenantId,
            bizDate,
            channel,
            total,
            total - differingAccounts,
            differingAccounts,
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

  /**
   * 一条对账差异（落 {@code reconciliation_diff} 一行）。
   *
   * <p>额度通道的差异不挂在具体赔案/结算单上，故 {@code claimNo}/{@code settlementNo} 为空； {@code localStage} 固定 {@code
   * QUOTA}，{@code remoteStatus} 承载<b>漂移列名</b> （{@code INVARIANT}
   * 表示守恒式不成立，其余值表示具体列）。差异单必须能回答"哪一列错了、错多少"—— 只写"账上有问题"，派单给风控后仍要人从流水翻起。
   */
  private record Violation(
      String claimNo,
      String settlementNo,
      DiffType type,
      BigDecimal localAmount,
      BigDecimal remoteAmount,
      String localStage,
      String remoteStatus) {}
}
