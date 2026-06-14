package com.bone.metadata.engine.starter.platform.model;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;

/** 只读映射 meta_entity，供 MetadataPlatformBridge 读取发布快照。 */
@Table("meta_entity")
public class PlatformMetaEntity extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "status", nullable = false)
  private Integer status;

  public Long getTenantId() {
    return tenantId;
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
}
