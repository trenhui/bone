package com.bone.metadata.engine.runtime.adapter.po;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 元数据字段表 {@code meta_field} 的只读持久化对象（PO）。
 *
 * <p>仅用于 {@code adapter} 层经 {@code bone-metadata-sdk} 的 {@code Repository} 读取， 并转换为引擎领域模型 {@code
 * SmartFieldMetadata}。本类不含领域行为，不得对外暴露。
 *
 * <p>字段对齐 {@code bone-metadata-server} 的 {@code MetaField}（@Table("meta_field")）。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("meta_field")
public class MetaFieldPo extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

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
}
