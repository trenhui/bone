package com.bone.procurement.governance.infrastructure;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人工终决台账实体：只存结论摘要/引用，禁 AI output 原文与身份证/银行账号。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_human_decision")
public class HumanDecisionEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "decision_id")
  private String decisionId;

  @Column(name = "capability_code")
  private String capabilityCode;

  @Column(name = "subject_ref")
  private String subjectRef;

  @Column(name = "ai_conclusion")
  private String aiConclusion;

  @Column(name = "ai_confidence")
  private Double aiConfidence;

  @Column(name = "final_conclusion")
  private String finalConclusion;

  @Column(name = "reviewer")
  private String reviewer;

  @Column(name = "reason")
  private String reason;

  @Column(name = "review_duration_ms")
  private Long reviewDurationMs;

  @Column(name = "override_accepted")
  private Boolean overrideAccepted;

  @Column(name = "evaluation_feedback")
  private Boolean evaluationFeedback;

  @Column(name = "decided_at")
  private Date decidedAt;

  @Version
  @Column(name = "version")
  private Integer version;
}
