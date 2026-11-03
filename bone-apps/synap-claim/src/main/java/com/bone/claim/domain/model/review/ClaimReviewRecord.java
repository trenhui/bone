package com.bone.claim.domain.model.review;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 复核 / 审核留痕（§9.5 A1，append-only，大额双人复核与权限校验的唯一事实来源）。
 *
 * <p><b>为什么 append-only</b>：复核结论是责任证据（谁在何时以什么依据批准/驳回了一笔赔付），不可改写。 与 {@link
 * com.bone.claim.domain.model.rule.ClaimRuleHit} 同款——由「不写 UPDATE + 不授 UPDATE 权限」保证， 而非缺列；{@link
 * TenantAbstractEntity} 仍注入审计列，工厂显式赋值 {@code created_at}/{@code updated_at}。 无 {@code @Version}。
 *
 * <p><b>不变量（§11.7 不变量 5 / §11.10）</b>：金额 ≥10000 的结算单 {@code PAID} 前须存在两条 {@code role} 分别为 {@code
 * REVIEW} 与 {@code SECOND_REVIEW} 的记录，且两条 {@code reviewer_id} 互异、 均不等于发起人 {@code
 * operator_id}。本实体只承载「一次复核动作」的事实，约束由写入方（双审编排）保证。
 */
@Getter
@Table("t_claim_review_record")
public class ClaimReviewRecord extends TenantAbstractEntity<Long> {

  /** 核赔（初审）。 */
  public static final String ROLE_REVIEW = "REVIEW";

  /** 双人复核（大额）。 */
  public static final String ROLE_SECOND_REVIEW = "SECOND_REVIEW";

  /** 调账审批。 */
  public static final String ROLE_APPROVE = "APPROVE";

  /** 批准。 */
  public static final String DECISION_APPROVED = "APPROVED";

  /** 驳回。 */
  public static final String DECISION_REJECTED = "REJECTED";

  /** 要求补充证据。 */
  public static final String DECISION_REQUEST_MORE_EVIDENCE = "REQUEST_MORE_EVIDENCE";

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "settlement_no")
  private String settlementNo;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "reviewer_id", nullable = false)
  private String reviewerId;

  @Column(name = "operator_id")
  private String operatorId;

  @Column(name = "amount")
  private BigDecimal amount;

  @Column(name = "decision", nullable = false)
  private String decision;

  @Column(name = "comment")
  private String comment;

  ClaimReviewRecord() {}

  public static ClaimReviewRecord create(
      Long tenantId,
      Long claimId,
      String settlementNo,
      String role,
      String reviewerId,
      String operatorId,
      BigDecimal amount,
      String decision,
      String comment,
      Instant createdAt) {
    ClaimReviewRecord record = new ClaimReviewRecord();
    record.setTenantId(tenantId);
    record.claimId = claimId;
    record.settlementNo = settlementNo == null ? "" : settlementNo;
    record.role = role == null ? ROLE_REVIEW : role;
    record.reviewerId = reviewerId == null ? "" : reviewerId;
    record.operatorId = operatorId == null ? "" : operatorId;
    record.amount = amount == null ? BigDecimal.ZERO : amount;
    record.decision = decision == null ? DECISION_APPROVED : decision;
    record.comment = comment == null ? "" : comment;
    Date now = Date.from(createdAt == null ? Instant.now() : createdAt);
    record.setCreatedAt(now);
    record.setUpdatedAt(now);
    return record;
  }
}
