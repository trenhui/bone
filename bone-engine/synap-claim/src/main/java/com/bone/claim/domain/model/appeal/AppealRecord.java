package com.bone.claim.domain.model.appeal;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 申诉记录聚合（A8，多租户）。
 *
 * <p>拒赔生效（45）或结案（50）后 48h 内受理（§6.6 / §7.6，监管《保险法》第 23/24 条 + PRD §8.5 「拒赔不是终点」R-MAN-04）。只追加：{@code
 * SUBMITTED → DECIDED}，复议结论维持（→45）或推翻重启审核（→41）。 48h 时限起算点取自案件进入终态的 {@code closedAt}（由 {@code
 * ClaimCase} 在终态跃迁时记录）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("appeal_record")
public class AppealRecord extends TenantAggregateRoot<Long> {

  /** 申诉受理法定时限：结案/拒赔生效后 48h（§6.6）。 */
  private static final Duration APPEAL_WINDOW = Duration.ofHours(48);

  @Column(name = "appeal_no", nullable = false)
  private String appealNo;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "claim_no", nullable = false)
  private String claimNo;

  /** 被申诉的原始终态：45（拒赔生效）/ 50（结案）。 */
  @Column(name = "original_status", nullable = false)
  private String originalStatus;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "submitted_by", nullable = false)
  private String submittedBy;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  /** 案件进入终态时间（48h 起算点）。 */
  @Column(name = "case_closed_at", nullable = false)
  private Instant caseClosedAt;

  @Column(name = "reviewed_by")
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_result")
  private String reviewResult;

  @Column(name = "review_reason")
  private String reviewReason;

  @Version
  @Column(name = "version")
  private Long version;

  private AppealRecord(
      Long id,
      Long tenantId,
      String appealNo,
      Long claimId,
      String claimNo,
      String originalStatus,
      String submittedBy,
      Instant caseClosedAt) {
    setId(id);
    setTenantId(tenantId);
    this.appealNo = appealNo;
    this.claimId = claimId;
    this.claimNo = claimNo;
    this.originalStatus = originalStatus;
    this.status = AppealStatus.SUBMITTED.name();
    this.submittedBy = submittedBy;
    this.submittedAt = Instant.now();
    this.caseClosedAt = caseClosedAt;
  }

  /** 发起申诉（受理）。超时或非终态 → 拒。 */
  public static AppealRecord submit(
      Long id,
      Long tenantId,
      String appealNo,
      Long claimId,
      String claimNo,
      String originalStatus,
      String submittedBy,
      Instant caseClosedAt) {
    if (!"45".equals(originalStatus) && !"50".equals(originalStatus)) {
      throw new DomainException("仅拒赔生效(45)或结案(50)案件可申诉，当前: " + originalStatus);
    }
    if (caseClosedAt == null) {
      throw new DomainException("案件终态时间缺失，无法计算申诉时限");
    }
    if (Duration.between(caseClosedAt, Instant.now()).compareTo(APPEAL_WINDOW) > 0) {
      throw new DomainException("申诉已超 48h 时限（§6.6），不可受理");
    }
    return new AppealRecord(
        id, tenantId, appealNo, claimId, claimNo, originalStatus, submittedBy, caseClosedAt);
  }

  /** 复议结论（维持/推翻）。 */
  public void review(AppealReviewResult result, String reviewedBy, String reason) {
    if (statusEnum() != AppealStatus.SUBMITTED) {
      throw new DomainException("仅受理中申诉可复议，当前: " + status);
    }
    this.status = AppealStatus.DECIDED.name();
    this.reviewedBy = reviewedBy;
    this.reviewedAt = Instant.now();
    this.reviewResult = result.name();
    this.reviewReason = reason;
  }

  public AppealStatus statusEnum() {
    return AppealStatus.valueOf(status);
  }

  public AppealReviewResult reviewResultEnum() {
    return reviewResult == null ? null : AppealReviewResult.valueOf(reviewResult);
  }
}
