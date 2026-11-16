package com.bone.claim.domain.model.feedback;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;

// AI 建议曝光 / 人工裁决回流（append-only 审计事实）。
// 不写 UPDATE、不设 @Version：裁决结论是责任证据，不可改写。
// 表内无金额 / 决策结论值 / 姓名证件，端点也不触碰案件状态与资金。
@Getter
@Table("t_claim_ai_feedback_event")
public class ClaimAiFeedbackEvent extends TenantAbstractEntity<Long> {

  public static final String TYPE_IMPRESSION = "IMPRESSION";
  public static final String TYPE_DISPOSITION = "DISPOSITION";

  @Column(name = "event_id", nullable = false)
  private String eventId;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "involvement", nullable = false)
  private String involvement;

  @Column(name = "disposition")
  private String disposition;

  @Column(name = "reason_type")
  private String reasonType;

  @Column(name = "comment_text")
  private String commentText;

  @Column(name = "modified_fields_json")
  private String modifiedFieldsJson;

  @Column(name = "scene", nullable = false)
  private String scene;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  ClaimAiFeedbackEvent() {}

  public static ClaimAiFeedbackEvent create(Long id, Long tenantId, AiFeedbackFact fact, Date now) {
    ClaimAiFeedbackEvent e = new ClaimAiFeedbackEvent();
    e.setId(id);
    e.setTenantId(tenantId);
    e.eventId = fact.eventId();
    e.claimId = fact.claimId();
    e.eventType = fact.eventType();
    e.traceId = fact.traceId() == null ? "" : fact.traceId();
    e.involvement = fact.involvement();
    e.disposition = fact.disposition();
    e.reasonType = fact.reasonType();
    e.commentText = fact.commentText();
    e.modifiedFieldsJson = fact.modifiedFieldsJson();
    e.scene = fact.scene();
    e.actorId = fact.actorId() == null ? "" : fact.actorId();
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    return e;
  }
}
