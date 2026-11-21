package com.bone.procurement.contract.infrastructure;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 合同审查持久化实体。
 *
 * <p>只存结构化摘要与引用：风险项 basis 经 PII/注入扫描后才落 JSON，不存合同正文或模型 output 原文； 快照只存引用。表 uk(tenant_id, review_id)
 * + uk(tenant_id, idempotency_key) 支撑跨租户隔离与幂等去重。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_contract_review")
public class ContractReviewEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "review_id")
  private String reviewId;

  @Column(name = "contract_no")
  private String contractNo;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "status")
  private String status;

  @Column(name = "ai_participation")
  private String aiParticipation;

  @Column(name = "risk_level")
  private String riskLevel;

  @Column(name = "risk_items_json")
  private String riskItemsJson;

  @Column(name = "ai_confidence")
  private Double aiConfidence;

  @Column(name = "model_version")
  private String modelVersion;

  @Column(name = "human_reason")
  private String humanReason;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "overrides_json")
  private String overridesJson;

  @Column(name = "snapshot_ref")
  private String snapshotRef;

  @Column(name = "human_finalized")
  private Boolean humanFinalized;

  @Version
  @Column(name = "version")
  private Integer version;
}
