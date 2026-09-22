package com.bone.masterdata.domain.model.lineage;

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

/** 数据血缘记录聚合。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_data_lineage")
public class LineageRecord extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "source_entity")
  private String sourceEntity;

  @Column(name = "source_field")
  private String sourceField;

  @Column(name = "transform_type")
  private String transformType;

  @Column(name = "target_entity")
  private String targetEntity;

  @Column(name = "target_field")
  private String targetField;

  @Column(name = "schema_name")
  private String schemaName;

  private LocalDateTime createdAt;

  public static LineageRecord create(
      Long id,
      String sourceEntity,
      String sourceField,
      String transformType,
      String targetEntity,
      String targetField,
      String schemaName) {
    LineageRecord record = new LineageRecord();
    record.id = id;
    record.sourceEntity = sourceEntity;
    record.sourceField = sourceField;
    record.transformType = transformType;
    record.targetEntity = targetEntity;
    record.targetField = targetField;
    record.schemaName = schemaName;
    record.createdAt = LocalDateTime.now();
    return record;
  }
}
