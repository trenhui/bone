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

  /**
   * 大额双人复核的<b>发起人</b>（token userId 落库，{@code REVIEW_FREEZE} 时写入）。
   *
   * <p>为什么必须落库而不是从资金流水反推：{@code findById} 不回填级联集合，复核时无法从流水判断 「刚才审核的是谁」；而这条值是 SoD
   * 的比较基准——复核人若等于发起人，双人复核就退化为一次点击。
   */
  @Column(name = "review_first_approver")
  private String reviewFirstApprover;

  /**
   * 审计时间戳。
   *
   * <p>必须<b>显式声明</b>并赋值：持久化层按 {@code @Column} 组装 INSERT 列，未声明的列会被整列省略， 而 DDL 里 {@code
   * created_at/updated_at} 是 {@code NOT NULL} 且无默认值——省略即插入失败（500）， 且只会在真正走到该写路径时爆炸。
   */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Transient
  @Cascade(foreignKey = "settlementId", orphanRemoval = false)
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
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
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

  /**
   * 审核通过（第一道认证）：小额直接硬预占，大额进入复核冻结等第二人放行。
   *
   * <p>复核人不再由请求体提名——名单一旦可自填，「双人复核」就只是把同一个人的两个马甲写进数组。 大额时这里只落 {@code reviewFirstApprover}（本次 token
   * 身份），第二人由 {@link #confirmReview} 以另一个真实身份完成；两条 {@code FundingEvent} 合起来才是「两次认证通过」的台账。
   */
  public void auditPass(String operatorId, String claimFirstReviewerId) {
    requireNotTerminal("审核");
    if (fundingStageEnum() != FundingStage.SOFT_RESERVED) {
      throw new DomainException("仅 SOFT_RESERVED 可推进至硬预占");
    }
    if (amount.compareTo(REVIEW_THRESHOLD) >= 0) {
      requireDistinctFromClaimReviewer(operatorId, claimFirstReviewerId, "审核人");
      this.reviewFirstApprover = operatorId;
      transition(FundingStage.REVIEW_FREEZE, operatorId);
    } else {
      transition(FundingStage.HARD_RESERVED, operatorId);
    }
  }

  /**
   * 大额复核放行（第二道认证）：{@code REVIEW_FREEZE → HARD_RESERVED}（§6.4「复核通过，解除冻结」）。
   *
   * <p>两条硬约束都在服务端判定，调用方无从声明：
   *
   * <ul>
   *   <li>复核人 ≠ 审核发起人（两次认证须两个身份，否则等于单人放款）；
   *   <li>复核人 ≠ 该赔案的第一审核人（核赔人既定价又放款，§10.3 职责分离失效）。
   * </ul>
   *
   * <p>复核不通过时不走本方法，由 {@link #cancel} 释放预占（§6.4 分支）。
   */
  public void confirmReview(String operatorId, String claimFirstReviewerId) {
    requireNotTerminal("复核");
    if (fundingStageEnum() != FundingStage.REVIEW_FREEZE) {
      throw new DomainException("仅复核冻结（REVIEW_FREEZE）可解除，当前: " + fundingStage);
    }
    if (operatorId == null || operatorId.isBlank()) {
      throw new DomainException("复核人不能为空");
    }
    if (operatorId.equals(reviewFirstApprover)) {
      throw new DomainException("复核人不得为本次审核的发起人（双人复核须两个不同身份）");
    }
    requireDistinctFromClaimReviewer(operatorId, claimFirstReviewerId, "复核人");
    transition(FundingStage.HARD_RESERVED, operatorId);
  }

  /** 支付成功（PAID）。 */
  public void pay(String operatorId) {
    requireNotTerminal("支付");
    if (!fundingStageEnum().isReadyToPay()) {
      throw new DomainException(
          fundingStageEnum() == FundingStage.REVIEW_FREEZE
              ? "大额资金冻结中，须经第二人复核放行后才可支付"
              : "仅已硬预占状态可支付");
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
    Instant now = Instant.now();
    this.updatedAt = now;
    this.fundingEvents.add(
        FundingEvent.of(
            null,
            getTenantId(),
            settlementNo,
            getId(),
            claimId,
            stage,
            amount,
            operatorId,
            accountType == null ? this.accountTypeEnum() : accountType,
            idempotencyKey(stage),
            now));
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

  /** 职责分离：资金侧审核/复核人不得是该赔案的核赔人（既定价又放款，§10.3）。 */
  private void requireDistinctFromClaimReviewer(
      String operatorId, String claimFirstReviewerId, String role) {
    if (operatorId == null || operatorId.isBlank()) {
      throw new DomainException(role + "不能为空");
    }
    if (claimFirstReviewerId == null || claimFirstReviewerId.isBlank()) {
      throw new DomainException("赔案未登记第一审核人，无法判定职责分离（§10.3）");
    }
    if (operatorId.equals(claimFirstReviewerId)) {
      throw new DomainException(role + "不得为该赔案的第一审核人（职责分离，§10.3）");
    }
  }
}
