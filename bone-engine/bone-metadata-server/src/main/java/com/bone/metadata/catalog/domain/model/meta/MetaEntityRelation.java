package com.bone.metadata.catalog.domain.model.meta;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_entity_relation")
public class MetaEntityRelation extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "source_entity_id", nullable = false)
  private Long sourceEntityId;

  @Column(name = "target_entity_id", nullable = false)
  private Long targetEntityId;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "source_field_id")
  private Long sourceFieldId;

  @Column(name = "target_field_id")
  private Long targetFieldId;

  @Column(name = "foreign_key_field")
  private String foreignKeyField;

  @Column(name = "is_required", nullable = false)
  private Boolean required;

  @Column(name = "cascade_type")
  private String cascadeType;

  @Column(name = "version", nullable = false)
  private Integer version;

  public static MetaEntityRelation create(
      Long id,
      Long tenantId,
      String name,
      Long sourceEntityId,
      Long targetEntityId,
      String relationType) {
    MetaEntityRelation r = new MetaEntityRelation();
    r.setId(id);
    r.tenantId = tenantId;
    r.name = name;
    r.sourceEntityId = sourceEntityId;
    r.targetEntityId = targetEntityId;
    r.type = relationType;
    r.required = false;
    r.version = 0;
    Date now = new Date();
    r.setCreatedAt(now);
    r.setUpdatedAt(now);
    r.setDeleted(false);
    return r;
  }

  public void update(
      String name,
      String relationType,
      Long sourceFieldId,
      Long targetFieldId,
      String foreignKeyField,
      Boolean required,
      String cascadeType) {
    this.name = name;
    this.type = relationType;
    this.sourceFieldId = sourceFieldId;
    this.targetFieldId = targetFieldId;
    this.foreignKeyField = foreignKeyField;
    if (required != null) {
      this.required = required;
    }
    this.cascadeType = cascadeType;
    bumpVersion();
    this.setUpdatedAt(new Date());
  }

  private void bumpVersion() {
    this.version = (this.version == null ? 0 : this.version) + 1;
  }
}
