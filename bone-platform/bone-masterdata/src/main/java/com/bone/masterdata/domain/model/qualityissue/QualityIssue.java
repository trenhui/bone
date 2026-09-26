package com.bone.masterdata.domain.model.qualityissue;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 质量整改工单（G11，UC-T9）：OPEN → FIXED → CLOSED（或 IGNORED）；未关闭工单是发布软门禁。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_quality_issue")
public class QualityIssue extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  private Long recordId;

  @Column(name = "check_id")
  private Long checkId;

  @Column(name = "rule_id")
  private Long ruleId;

  @Column(name = "issue_desc")
  private String issueDesc;

  /** 严重度：HIGH / MEDIUM / LOW。 */
  private String severity;

  /** 状态：OPEN / FIXED / CLOSED / IGNORED。 */
  private String status;

  @Column(name = "assignee_id")
  private Long assigneeId;

  @Column(name = "due_at")
  private LocalDateTime dueAt;

  @Column(name = "resolved_by")
  private Long resolvedBy;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static QualityIssue open(
      Long id,
      Long masterDataEntityId,
      Long recordId,
      Long checkId,
      Long ruleId,
      String issueDesc,
      String severity,
      Long assigneeId,
      LocalDateTime dueAt) {
    QualityIssue issue = new QualityIssue();
    issue.id = id;
    issue.masterDataEntityId = masterDataEntityId;
    issue.recordId = recordId;
    issue.checkId = checkId;
    issue.ruleId = ruleId;
    issue.issueDesc = issueDesc;
    issue.severity = severity == null || severity.isBlank() ? "MEDIUM" : severity;
    issue.status = "OPEN";
    issue.assigneeId = assigneeId;
    issue.dueAt = dueAt;
    issue.createdAt = LocalDateTime.now();
    issue.updatedAt = LocalDateTime.now();
    return issue;
  }

  public void fix(Long resolvedBy) {
    if (!"OPEN".equals(this.status)) {
      throw new DomainException("仅待整改的工单可标记已整改");
    }
    this.status = "FIXED";
    this.resolvedBy = resolvedBy;
    this.resolvedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void close() {
    if (!"FIXED".equals(this.status)) {
      throw new DomainException("仅已整改的工单可关闭（复检通过后）");
    }
    this.status = "CLOSED";
    this.updatedAt = LocalDateTime.now();
  }

  public void ignore(Long resolvedBy) {
    if (!"OPEN".equals(this.status)) {
      throw new DomainException("仅待整改的工单可忽略");
    }
    this.status = "IGNORED";
    this.resolvedBy = resolvedBy;
    this.resolvedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
