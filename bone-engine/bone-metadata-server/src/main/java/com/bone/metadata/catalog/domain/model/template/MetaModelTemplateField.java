package com.bone.metadata.catalog.domain.model.template;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 平台模型模板的默认字段集（G3）：实例化时逐条复制为租户 {@code meta_field}。 */
@Getter
@NoArgsConstructor
@Table("meta_model_template_field")
public class MetaModelTemplateField extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "template_id", nullable = false)
  private Long templateId;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  /** 字段类型：string / integer / decimal / boolean / date / datetime / text（与 meta_field.type 同口径）。 */
  @Column(name = "field_type", nullable = false)
  private String fieldType;

  @Column(name = "length")
  private Integer length;

  @Column(name = "is_required", nullable = false)
  private Boolean required;

  @Column(name = "is_unique", nullable = false)
  private Boolean unique;

  @Column(name = "default_value")
  private String defaultValue;

  @Column(name = "comment")
  private String comment;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  public static MetaModelTemplateField create(
      Long id,
      Long tenantId,
      Long templateId,
      String code,
      String name,
      String displayName,
      String fieldType,
      Integer length,
      Boolean required,
      Boolean unique,
      String defaultValue,
      String comment,
      Integer sortOrder) {
    MetaModelTemplateField f = new MetaModelTemplateField();
    f.setId(id);
    f.tenantId = tenantId;
    f.templateId = templateId;
    f.code = code;
    f.name = name;
    f.displayName = displayName;
    f.fieldType = fieldType;
    f.length = length;
    f.required = required != null ? required : false;
    f.unique = unique != null ? unique : false;
    f.defaultValue = defaultValue;
    f.comment = comment;
    f.sortOrder = sortOrder != null ? sortOrder : 0;
    Date now = new Date();
    f.setCreatedAt(now);
    f.setUpdatedAt(now);
    f.setDeleted(false);
    return f;
  }
}
