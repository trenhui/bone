package com.bone.studio.generator.domain.catalog.model;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;

/** 只读映射 meta_entity，供 generator 读取发布快照 */
@Table("meta_entity")
public class CatalogMetaEntity extends AbstractEntity<Long> {

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

  @Column(name = "status", nullable = false)
  private Integer status;

  @Column(name = "delivery_mode", nullable = false)
  private Integer deliveryMode;

  public Long getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getTableName() {
    return tableName;
  }

  public Integer getStatus() {
    return status;
  }

  public Integer getDeliveryMode() {
    return deliveryMode;
  }
}
