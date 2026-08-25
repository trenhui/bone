package com.bone.metadata.engine.adapter.po;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 元数据实体表 {@code meta_entity} 的只读持久化对象（PO）。
 *
 * <p>仅用于 {@code adapter} 层经 {@code bone-metadata-sdk} 的 {@code Repository} 读取已发布元数据， 并转换为引擎领域模型
 * {@code EntityMetadata}。本类不含领域行为，不得对外暴露。
 *
 * <p>字段对齐 {@code bone-metadata-server} 的 {@code MetaEntity}（@Table("meta_entity")）， 但保持为独立 PO 以避免
 * engine 反向依赖 server（循环依赖风险）。属于防腐层（ACL）的外部表示。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("meta_entity")
public class MetaEntityPo extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "description")
  private String description;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "type", nullable = false)
  private Integer type;

  @Column(name = "delivery_mode", nullable = false)
  private Integer deliveryMode;

  @Column(name = "status", nullable = false)
  private Integer status;

  @Column(name = "is_builtin", nullable = false)
  private Boolean builtin;

  @Column(name = "icon")
  private String icon;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @Column(name = "version", nullable = false)
  private Integer version;
}
