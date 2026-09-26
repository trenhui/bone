package com.bone.masterdata.domain.model.drift;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 模型漂移（G16，UC-T10）：元数据↔主数据结构对账结果；禁止自动同步，破坏性变更须人工处置。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_model_drift")
public class ModelDrift extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  @Column(name = "meta_entity_id")
  private Long metaEntityId;

  /** 漂移类型：FIELD_ADDED / FIELD_REMOVED / TYPE_CHANGED / NAME_CHANGED。 */
  @Column(name = "drift_type")
  private String driftType;

  @Column(name = "field_code")
  private String fieldCode;

  @Column(name = "old_value")
  private String oldValue;

  @Column(name = "new_value")
  private String newValue;

  /** 是否破坏性变更（FIELD_REMOVED / TYPE_CHANGED）。 */
  private Boolean destructive;

  /** 处置状态：PENDING / SYNCED / IGNORED / BLOCKED。 */
  private String status;

  @Column(name = "handled_by")
  private Long handledBy;

  @Column(name = "handled_at")
  private LocalDateTime handledAt;

  @Column(name = "detected_at")
  private LocalDateTime detectedAt;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ModelDrift detected(
      Long id,
      Long masterDataEntityId,
      Long metaEntityId,
      String driftType,
      String fieldCode,
      String oldValue,
      String newValue,
      boolean destructive) {
    ModelDrift drift = new ModelDrift();
    drift.id = id;
    drift.masterDataEntityId = masterDataEntityId;
    drift.metaEntityId = metaEntityId;
    drift.driftType = driftType;
    drift.fieldCode = fieldCode;
    drift.oldValue = oldValue;
    drift.newValue = newValue;
    drift.destructive = destructive;
    drift.status = "PENDING";
    drift.detectedAt = LocalDateTime.now();
    drift.createdAt = LocalDateTime.now();
    drift.updatedAt = LocalDateTime.now();
    return drift;
  }

  public void handle(String targetStatus, Long handledBy) {
    this.status = targetStatus;
    this.handledBy = handledBy;
    this.handledAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
