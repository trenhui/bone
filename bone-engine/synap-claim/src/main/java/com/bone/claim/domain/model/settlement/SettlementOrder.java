package com.bone.claim.domain.model.settlement;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 结算单聚合根（A7，多租户）。
 *
 * <p>资金 Saga 编排者（§7.5）：状态机只由本聚合推进，AI 层无写入口。所有跨聚合写（额度占用/释放） 经独立事务的 {@code QuotaApplicationService}
 * 完成（CORE-06 一事务一聚合），不在此处直接改额度账户。
 *
 * <p>幂等：同一案件同一金额重复支付指令直接返回首次结果（由应用层以 {@code settlementNo} 去重实现， 本聚合仅保证状态机不可逆向）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("settle_order")
public class SettlementOrder extends TenantAggregateRoot<Long> {

  private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("10000.00");

  @Column(name = "settlement_no", nullable = false)
  private String settlementNo;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "claim_no", nullable = false)
  private String claimNo;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "funding_stage", nullable = false)
  private String fundingStage;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  /** 占额周期序号（§7.5）：同一案件经历 SOFT→HARD→RELEASED→再 SOFT 或多轮退款时递增。 */
  @Column(name = "attempt", nullable = false)
  private Integer attempt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "status", nullable = false)
  private String status;

  @Transient
  @Cascade(foreignKey = "settlement_id", orphanRemoval = false)
  private List<FundingEvent> fundingEvents = new ArrayList<>();

  @Version
  @Column(name = "version")
  private Long version;

  private SettlementOrder(
      Long id,
      Long tenantId,
      String settlementNo,
      Long claimId,
      String claimNo,
      BigDecimal amount,
      AccountType accountType,
      Integer attempt) {
    setId(id);
    setTenantId(tenantId);
    this.settlementNo = settlementNo;
    this.claimId = claimId;
    this.claimNo = claimNo;
    this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    this.accountType = accountType == null ? AccountType.NO_ACCOUNT.name() : accountType.name();
    this.attempt = attempt;
    this.fundingStage = FundingStage.SOFT_RESERVED.name();
    this.status = "OPEN";
  }

  /** 建单（软占 SOFT_RESERVED）。幂等去重由应用层基于 {@code settlementNo} 完成。 */
  public static SettlementOrder create(
      Long id,
      Long tenantId,
      String settlementNo,
      Long claimId,
      String claimNo,
      BigDecimal amount,
      AccountType accountType,
      Integer attempt) {
    if (amount == null || amount.signum() <= 0) {
      throw new DomainException("结算金额必须大于 0");
    }
    SettlementOrder order =
        new SettlementOrder(
            id, tenantId, settlementNo, claimId, claimNo, amount, accountType, attempt);
    order.appendEvent(FundingStage.SOFT_RESERVED, "system", null);
    return order;
  }

  /** 审核通过进入硬预占（≥1 万先 REVIEW_FREEZE，须两个非第一审核人 reviewerId）。 */
  public void auditPass(String operatorId, List<String> reviewerIds, String firstReviewerId) {
    requireNotTerminal("审核");
    if (fundingStageEnum() != FundingStage.SOFT_RESERVED) {
      throw new DomainException("仅 SOFT_RESERVED 可推进至硬预占");
    }
    if (amount.compareTo(REVIEW_THRESHOLD) >= 0) {
      requireDualReview(reviewerIds, firstReviewerId);
      transition(FundingStage.REVIEW_FREEZE, operatorId);
    } else {
      transition(FundingStage.HARD_RESERVED, operatorId);
    }
  }

  /** 支付成功（PAID）。 */
  public void pay(String operatorId) {
    requireNotTerminal("支付");
    if (!fundingStageEnum().isReadyToPay()) {
      throw new DomainException("仅已硬预占/复核冻结状态可支付");
    }
    this.fundingStage = FundingStage.PAID.name();
    this.paidAt = Instant.now();
    this.status = "CLOSED";
    appendEvent(FundingStage.PAID, operatorId, null);
  }

  /** 撤销/拒赔/支付失败 → 释放预占（RELEASED / FAILED）。 */
  public void cancel(String operatorId, boolean payFailed) {
    requireNotTerminal("撤销");
    this.fundingStage = payFailed ? FundingStage.FAILED.name() : FundingStage.RELEASED.name();
    this.status = "CLOSED";
    appendEvent(FundingStage.valueOf(this.fundingStage), operatorId, null);
  }

  /** 已付退票退款（REFUNDED，须红冲发票，§7.5 / §11.1.2）。 */
  public void refund(String operatorId) {
    if (fundingStageEnum() != FundingStage.PAID) {
      throw new DomainException("仅 PAID 可退款");
    }
    this.fundingStage = FundingStage.REFUNDED.name();
    this.status = "CLOSED";
    appendEvent(FundingStage.REFUNDED, operatorId, null);
  }

  public FundingStage fundingStageEnum() {
    return FundingStage.valueOf(fundingStage);
  }

  public AccountType accountTypeEnum() {
    return AccountType.valueOf(accountType);
  }

  public boolean isPaid() {
    return fundingStageEnum().isPaid();
  }

  /** 资金流水（包级可见；理由同 blueprint：{@code findById} 不回填集合）。 */
  List<FundingEvent> fundingEvents() {
    return Collections.unmodifiableList(fundingEvents);
  }

  // ===================== 内部 =====================

  private void transition(FundingStage target, String operatorId) {
    this.fundingStage = target.name();
    appendEvent(target, operatorId, null);
  }

  private void appendEvent(FundingStage stage, String operatorId, AccountType accountType) {
    this.fundingEvents.add(
        FundingEvent.of(
            null,
            getTenantId(),
            settlementNo,
            claimId,
            stage,
            amount,
            operatorId,
            accountType == null ? this.accountTypeEnum() : accountType,
            idempotencyKey(stage),
            Instant.now()));
  }

  /** 幂等键：{@code {claimNo}:{stage}:{attempt}}（§7.5）。 */
  private String idempotencyKey(FundingStage stage) {
    return claimNo + ":" + stage.name() + ":" + attempt;
  }

  private void requireNotTerminal(String action) {
    if (fundingStageEnum().isTerminal()) {
      throw new DomainException("资金已终态（" + fundingStage + "），不可" + action);
    }
  }

  private void requireDualReview(List<String> reviewerIds, String firstReviewerId) {
    if (reviewerIds == null || reviewerIds.size() < 2) {
      throw new DomainException("金额 ≥10000 须双人复核（两个 reviewerId）");
    }
    long distinct =
        reviewerIds.stream().filter(Objects::nonNull).map(String::trim).distinct().count();
    if (distinct < 2) {
      throw new DomainException("双人复核须为两名不同复核人");
    }
    boolean involvesFirst = reviewerIds.stream().anyMatch(r -> Objects.equals(r, firstReviewerId));
    if (involvesFirst) {
      throw new DomainException("双人复核人不得包含第一审核人（职责分离，§10.3）");
    }
  }
}
