package com.bone.masterdata.domain.model.feedback;

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

/** 下游数据问题反馈（G17，UC-C4）：订阅应用反向纠错；PENDING → ACCEPTED → DONE / REJECTED。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_feedback")
public class DataFeedback extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  /** 关联记录ID（空表示新增申请）。 */
  @Column(name = "record_id")
  private Long recordId;

  /** 反馈来源应用ID。 */
  @Column(name = "app_id")
  private Long appId;

  /** 类型：CORRECTION-修正 / ADDITION-新增 / DUPLICATE-重复。 */
  @Column(name = "feedback_type")
  private String feedbackType;

  private String content;

  /** 建议数据（JSON）。 */
  @Column(name = "suggested_data")
  private String suggestedData;

  /** 状态：PENDING / ACCEPTED / REJECTED / DONE。 */
  private String status;

  @Column(name = "assignee_id")
  private Long assigneeId;

  @Column(name = "handled_by")
  private Long handledBy;

  @Column(name = "handled_result")
  private String handledResult;

  @Column(name = "handled_at")
  private LocalDateTime handledAt;

  @Column(name = "submitted_by")
  private Long submittedBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static DataFeedback submit(
      Long id,
      Long masterDataEntityId,
      Long recordId,
      Long appId,
      String feedbackType,
      String content,
      String suggestedData,
      Long submittedBy) {
    DataFeedback feedback = new DataFeedback();
    feedback.id = id;
    feedback.masterDataEntityId = masterDataEntityId;
    feedback.recordId = recordId;
    feedback.appId = appId;
    feedback.feedbackType =
        feedbackType == null || feedbackType.isBlank() ? "CORRECTION" : feedbackType;
    feedback.content = content;
    feedback.suggestedData = suggestedData;
    feedback.status = "PENDING";
    feedback.submittedBy = submittedBy;
    feedback.createdAt = LocalDateTime.now();
    feedback.updatedAt = LocalDateTime.now();
    return feedback;
  }

  public void accept(Long assigneeId) {
    if (!"PENDING".equals(this.status)) {
      throw new DomainException("仅待处理反馈可受理");
    }
    this.status = "ACCEPTED";
    this.assigneeId = assigneeId;
    this.updatedAt = LocalDateTime.now();
  }

  public void reject(Long handledBy, String result) {
    if (!"PENDING".equals(this.status) && !"ACCEPTED".equals(this.status)) {
      throw new DomainException("反馈已终态，不能驳回");
    }
    this.status = "REJECTED";
    this.handledBy = handledBy;
    this.handledResult = result;
    this.handledAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /** 处理完成（已按建议修正主数据后调用）。 */
  public void complete(Long handledBy, String result) {
    if (!"ACCEPTED".equals(this.status)) {
      throw new DomainException("仅已受理的反馈可标记完成");
    }
    this.status = "DONE";
    this.handledBy = handledBy;
    this.handledResult = result;
    this.handledAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
