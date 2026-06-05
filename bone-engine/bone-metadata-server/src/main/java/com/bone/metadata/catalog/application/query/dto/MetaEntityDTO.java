package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

@Data
public class MetaEntityDTO {
  private Long id;
  private String name;
  private String code;
  private String displayName;
  private String description;
  private String tableName;
  private Integer type;
  private Integer deliveryMode;
  private String deliveryModeLabel;
  private Integer status;
  private String statusLabel;
  private Integer sortOrder;
  private String icon;
  /** 乐观锁版本（PUT 使用 If-Match: "v{version}"） */
  private Integer version;
}
