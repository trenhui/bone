package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.claim.projection.ClaimSettlementRef;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaLedgerChain;
import com.bone.claim.domain.model.quota.QuotaProjection;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.model.reconciliation.DiffType;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.ReconciliationReport;
import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.FundingStage;
import com.bone.claim.domain.model.settlement.FundingStageChain;
import com.bone.claim.domain.model.settlement.ReviewEvidence;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.ReconciliationDiffRepository;
import com.bone.claim.domain.repository.ReconciliationReportRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.core.annotation.NoDomainEvent;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资金对账应用服务（§11.7，一期内部一致性对账）。
 *
 * <p>由 {@code adapter.schedule} 的 {@code ReconciliationJob} 逐租户驱动（ADR-0030）。一期三条通道：
 *
 * <ul>
 *   <li>{@link ReconciliationReport#CHANNEL_QUOTA}：额度账户三条判据——①守恒式 {@code initial == claimed +
 *       frozen + available}，破坏记 {@link DiffType#QUOTA_BREAK}；②<b>投影 = 事实</b>：按 {@code
 *       t_quota_transaction} 重算 {@code frozen/claimed/deductible_used/cap_used} 并与账户行比对，不等记 {@link
 *       DiffType#PROJECTION_DRIFT}（§11.7 不变量 1 ①）；③<b>余额串链</b>：逐条校验 {@code before_amount} 接得上上一条同列
 *       {@code after_amount}、且差额等于本笔金额，断裂记 {@link DiffType#LEDGER_CHAIN_BREAK}（不变量 1 ④）。
 *   <li>{@link ReconciliationReport#CHANNEL_PAYMENT}：结算单 vs 资金流水四判据——①{@code PAID} 结算单须有等额 {@code
 *       PAID} 流水；②非 {@code PAID} 结算单末条流水阶段须与结算阶段一致；③<b>阶段单调</b>：整条流水按状态机串一遍，非法迁移与事件时间倒序记 {@link
 *       DiffType#STAGE_REGRESSION}（不变量 3）；④<b>双人复核留痕</b>：大额结算的 {@code review_first_approver}
 *       与两条认证事件身份 须构成两次独立认证，缺失记 {@link DiffType#REVIEW_EVIDENCE_MISSING}（不变量 5）。
 *   <li>{@link ReconciliationReport#CHANNEL_CASE_SETTLE}：案件 ↔ 结算金额一致（不变量 6）——结案了却没有结算单记 {@link
 *       DiffType#MISSING_LOCAL}；净已付（{@code Σ PAID − Σ REFUNDED}）≠ 最新理算总额记 {@link
 *       DiffType#CASE_SETTLE_MISMATCH}。
 * </ul>
 *
 * <p><b>为什么额度通道要三条判据</b>：守恒式只校验账户内部三值自洽，一根指针错到底（{@code claimed} 少记一笔、 {@code deductible_used}
 * 多记一次）照样"自洽"；投影判据能证明"现值 = Σ流水"，但证明不了"流水之间接得上"——并发丢失更新后两笔流水都从同一个旧值出发， 而投影仍等于 Σ流水（投影本就是按流水算的）。
 * 三者分别回答"三值自洽吗""值等于事实吗""过程连得上吗"，任何一条被省掉，另一条都会在它管不到的场景里恒绿。
 *
 * <p><b>为什么支付通道要四条判据</b>：①②只看「末条」与「PAID 等额」，一串被倒拨过的流水（PAID 后又追加 SOFT）在末条同步改过之后与结算单仍然相等；
 * ③把整条链按状态机串一遍才看得出「钱的状态被往回拨过」，且它是唯一能发现「事件时间倒序」（补数/乱序重放）的判据；④判的不是钱的走向而是<b>谁点的</b>——
 * 绕过复核门的单子在流水上完全合法（SOFT→HARD→PAID 逐条都对），只有把发起人与两条认证事件的身份并起来看，才知道双人复核是否真的发生过。
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

  /** 业务日划界的时区（与 {@code ReconciliationJob} 一致：理赔作业按本地日历排班）。 */
  private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

  private final TenantPort tenantPort;
  private final QuotaAccountRepository quotaAccountRepository;
  private final SettlementRepository settlementRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final ReconciliationReportRepository reportRepository;
  private final ReconciliationDiffRepository diffRepository;

  /** 对当前租户某业务日执行 QUOTA + PAYMENT + CASE_SETTLE 三通道对账（须在租户上下文内调用）。 */
  @Transactional
  public void reconcileForTenant(LocalDate bizDate) {
    long tenantId = tenantPort.currentTenantId();
    reconcileQuota(tenantId, bizDate);
    reconcilePayment(tenantId, bizDate);
    reconcileCaseSettle(tenantId, bizDate);
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
      List<QuotaTransaction> ledger = quotaAccountRepository.findTransactions(account.getId());
      List<QuotaProjection.Drift> drifts = QuotaProjection.rebuild(ledger).driftAgainst(account);
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
      // 判据 ③：余额串链（不变量 1 ④）。判据 ② 判"账户现值 = Σ流水"，对"两笔操作从同一个旧值出发"无感——
      // 并发丢失更新后投影仍等于 Σ流水（投影本就是按流水算的），只有逐条串 before/after 才看得见。
      for (QuotaLedgerChain.Break chainBreak : QuotaLedgerChain.check(ledger)) {
        violations.add(
            new Violation(
                "",
                "",
                DiffType.LEDGER_CHAIN_BREAK,
                chainBreak.recorded(),
                chainBreak.expected(),
                "QUOTA",
                chainBreak.column() + ":" + chainBreak.kind().name()));
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
      // 判据 ③：整条流水按状态机串一遍（不变量 3）。判据 ①② 只看「末条」与「PAID 等额」，
      // 对「PAID 之后又追加 SOFT」这类倒拨无感——末条在结算单同步改过之后仍然相等。
      for (FundingStageChain.Breach breach : FundingStageChain.check(events)) {
        violations.add(
            new Violation(
                settlement.getClaimNo(),
                settlement.getSettlementNo(),
                DiffType.STAGE_REGRESSION,
                null,
                null,
                breach.from(),
                breach.to()));
      }
      // 判据 ④：大额双人复核留痕（不变量 5）。判据 ①②③ 判的是钱的走向，判不出"这次放款是不是一个人点的"——
      // 绕过复核门的单子在流水上完全合法（SOFT→HARD→PAID），只有把 review_first_approver 与两条认证事件
      // 的身份并起来看，才知道"两次认证"是否真的存在过。
      for (ReviewEvidence.Gap gap :
          ReviewEvidence.check(
              settlement.getAmount(),
              SettlementOrder.reviewThreshold(),
              settlement.getReviewFirstApprover(),
              events)) {
        violations.add(
            new Violation(
                settlement.getClaimNo(),
                settlement.getSettlementNo(),
                DiffType.REVIEW_EVIDENCE_MISSING,
                settlement.getAmount(),
                null,
                settlement.getFundingStage(),
                gap.name()));
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

  /**
   * 案件 ↔ 结算金额一致（§11.7 不变量 6）。
   *
   * <p><b>判决式为什么不是文档里那句 {@code approved_amount = ΣPAID}</b>：结算金额取的是<b>理算结果</b>（资金权威口径），{@code
   * approved_amount} 是核赔决策时的快照——核减过的案件两者天然不等。按快照判会把每一天的正常案件全判成差异， 报告长期飘红之后真差异就没人看了。故判决式改为「净已付 =
   * 最新理算总额」：净已付 = Σ{@code PAID} − Σ{@code REFUNDED}，多轮补赔/退款后才是真实的钱。
   *
   * <p><b>两条判据分工</b>：<b>结案无单</b>记 {@link DiffType#MISSING_LOCAL}（这类赔案在结算表里一行都没有，从结算侧出发永远扫不到， 必须按
   * {@code closed_at} 主动扫结案侧）；<b>金额不符</b>记 {@link DiffType#CASE_SETTLE_MISMATCH}。
   *
   * <p><b>三种情况刻意不判</b>（不是漏判，是判决式在这些状态下无意义）：
   *
   * <ul>
   *   <li><b>有在途结算单</b>（SOFT/HARD/REVIEW_FREEZE）：钱还没走完，此刻的净已付当然不等于应付。
   *   <li><b>出现过退款</b>（存在 {@code REFUNDED} 结算单）：退款后该案须重新理算再结，新旧金额不可比；强行比会得到一条永不消失的假差异。
   *   <li><b>应付为 0 或查不到理算</b>：零赔付结案本就不该有结算单（建单要求金额 > 0），判它等于把正常结案变差异。
   * </ul>
   */
  private void reconcileCaseSettle(long tenantId, LocalDate bizDate) {
    if (reportRepository.findReport(tenantId, bizDate, ReconciliationReport.CHANNEL_CASE_SETTLE)
        != null) {
      return;
    }
    // 待判赔案 = 当日结案的 ∪ 当日有结算单的。两侧都要扫：只扫结算侧会漏「结案但没单」，
    // 只扫结案侧会漏「今天付了钱但案子早就结了」（补赔/重结）。
    Map<Long, String> claimNos = new LinkedHashMap<>();
    for (ClaimSettlementRef ref :
        claimCaseRepository.findFinishedBetween(dayStart(bizDate), dayEnd(bizDate))) {
      if (ref != null && ref.claimId() != null) {
        claimNos.putIfAbsent(ref.claimId(), ref.claimNo());
      }
    }
    for (SettlementOrder settlement : settlementRepository.findByBizDate(bizDate)) {
      claimNos.putIfAbsent(settlement.getClaimId(), settlement.getClaimNo());
    }

    List<Violation> violations = new ArrayList<>();
    // 差异按赔案计一份（一个赔案可能同时命中两条判据），否则 balanced + diff > total
    Set<Long> differingClaims = new HashSet<>();
    BigDecimal totalPaid = ZERO;
    BigDecimal totalExpected = ZERO;
    for (Map.Entry<Long, String> entry : claimNos.entrySet()) {
      Long claimId = entry.getKey();
      String claimNo = entry.getValue();
      List<SettlementOrder> orders = settlementRepository.findByClaimId(claimId);
      BigDecimal expected = expectedAmount(claimId);
      BigDecimal netPaid = netPaid(orders);
      totalPaid = totalPaid.add(netPaid);
      if (expected != null) {
        totalExpected = totalExpected.add(expected);
      }

      if (orders.isEmpty()) {
        if (expected == null || expected.signum() <= 0) {
          continue; // 无应付 ⇒ 不需要结算单（零赔付结案）
        }
        violations.add(
            new Violation(
                claimNo, "", DiffType.MISSING_LOCAL, ZERO, expected, "", "NO_SETTLEMENT"));
        differingClaims.add(claimId);
        continue;
      }
      if (orders.stream().anyMatch(o -> !o.fundingStageEnum().isTerminal())) {
        continue; // 在途：钱还没走完，不判
      }
      if (orders.stream().anyMatch(o -> o.fundingStageEnum() == FundingStage.REFUNDED)) {
        continue; // 退款后须重结，新旧金额不可比
      }
      if (expected == null) {
        // 付了钱却查不到理算依据：账单本身缺一环，派给资金岗核对，不当金额差异处理
        violations.add(
            new Violation(
                claimNo,
                lastSettlementNo(orders),
                DiffType.AMOUNT_MISMATCH,
                netPaid,
                null,
                "NET_PAID",
                "NO_ADJUSTMENT"));
        differingClaims.add(claimId);
        continue;
      }
      if (netPaid.compareTo(expected) != 0) {
        violations.add(
            new Violation(
                claimNo,
                lastSettlementNo(orders),
                DiffType.CASE_SETTLE_MISMATCH,
                netPaid,
                expected,
                "NET_PAID",
                "ADJUST_TOTAL"));
        differingClaims.add(claimId);
      }
    }
    saveReport(
        tenantId,
        bizDate,
        ReconciliationReport.CHANNEL_CASE_SETTLE,
        claimNos.size(),
        differingClaims.size(),
        totalPaid,
        totalExpected,
        violations);
  }

  /** 最新理算总额（资金权威口径）；未理算返回 {@code null}（不是 0——"没算过"与"算出 0"判法不同）。 */
  private BigDecimal expectedAmount(Long claimId) {
    Adjustment adjustment = adjustmentRepository.findLatestByClaimId(claimId);
    return adjustment == null ? null : adjustment.getTotalAmount();
  }

  /** 净已付 = Σ{@code PAID} − Σ{@code REFUNDED}（退款把已付的钱退回去，钱就不再在赔案上）。 */
  private static BigDecimal netPaid(List<SettlementOrder> orders) {
    BigDecimal net = ZERO;
    for (SettlementOrder order : orders) {
      BigDecimal amount = order.getAmount() == null ? ZERO : order.getAmount();
      if (order.fundingStageEnum() == FundingStage.PAID) {
        net = net.add(amount);
      } else if (order.fundingStageEnum() == FundingStage.REFUNDED) {
        net = net.subtract(amount);
      }
    }
    return net;
  }

  private static String lastSettlementNo(List<SettlementOrder> orders) {
    return orders.get(orders.size() - 1).getSettlementNo();
  }

  private static Timestamp dayStart(LocalDate bizDate) {
    return Timestamp.from(bizDate.atStartOfDay(BIZ_ZONE).toInstant());
  }

  private static Timestamp dayEnd(LocalDate bizDate) {
    return Timestamp.from(bizDate.plusDays(1).atStartOfDay(BIZ_ZONE).toInstant());
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
