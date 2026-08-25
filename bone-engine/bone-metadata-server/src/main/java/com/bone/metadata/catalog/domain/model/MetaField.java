package com.bone.metadata.catalog.domain.model;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_field")
public class MetaField extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

  @Column(name = "module_id")
  private Long moduleId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "length")
  private Integer length;

  // 用 numeric_precision 规避 MySQL 保留字 precision（metadata-sdk 的 INSERT 列名未转义保留字）
  @Column(name = "numeric_precision")
  private Integer precision;

  @Column(name = "is_required", nullable = false)
  private Boolean required;

  @Column(name = "is_unique", nullable = false)
  private Boolean unique;

  @Column(name = "is_pk", nullable = false)
  private Boolean pk;

  @Column(name = "is_indexed", nullable = false)
  private Boolean indexed;

  @Column(name = "default_value")
  private String defaultValue;

  @Column(name = "comment")
  private String comment;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @Column(name = "version", nullable = false)
  private Integer version;

  public static MetaField create(
      Long id,
      Long tenantId,
      Long entityId,
      String name,
      String code,
      String displayName,
      String fieldType) {
    return create(
        id,
        tenantId,
        entityId,
        name,
        code,
        displayName,
        fieldType,
        null,
        false,
        false,
        null,
        null,
        0,
        null);
  }

  public static MetaField create(
      Long id,
      Long tenantId,
      Long entityId,
      String name,
      String code,
      String displayName,
      String fieldType,
      Integer length,
      Boolean required,
      Boolean unique,
      String defaultValue,
      String comment,
      Integer sortOrder,
      Long moduleId) {
    MetaField f = new MetaField();
    f.setId(id);
    f.tenantId = tenantId;
    f.entityId = entityId;
    f.moduleId = moduleId;
    f.name = name;
    f.code = code;
    f.displayName = displayName;
    f.type = fieldType;
    f.length = length;
    f.required = required != null ? required : false;
    f.unique = unique != null ? unique : false;
    f.pk = false;
    f.indexed = false;
    f.defaultValue = defaultValue;
    f.comment = comment;
    f.sortOrder = sortOrder != null ? sortOrder : 0;
    f.version = 0;
    Date now = new Date();
    f.setCreatedAt(now);
    f.setUpdatedAt(now);
    f.setDeleted(false);
    return f;
  }

  public void update(
      String displayName,
      String fieldType,
      Integer length,
      Boolean required,
      Boolean unique,
      String defaultValue,
      String comment,
      Integer sortOrder) {
    this.displayName = displayName;
    this.type = fieldType;
    this.length = length;
    this.required = required != null ? required : false;
    this.unique = unique != null ? unique : false;
    this.defaultValue = defaultValue;
    this.comment = comment;
    if (sortOrder != null) {
      this.sortOrder = sortOrder;
    }
    bumpVersion();
    this.setUpdatedAt(new Date());
  }

  private void bumpVersion() {
    this.version = (this.version == null ? 0 : this.version) + 1;
  }
}
