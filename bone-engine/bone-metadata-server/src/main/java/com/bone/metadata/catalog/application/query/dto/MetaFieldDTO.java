package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

@Data
public class MetaFieldDTO {
  private Long id;
  private Long entityId;
  private String name;
  private String code;
  private String displayName;
  private String type;
  private Integer length;
  private Boolean required;
  private Boolean unique;
  private Integer sortOrder;

  /** 字段注释 */
  private String comment;

  /** 创建时间 */
  private java.util.Date createdAt;

  /** 乐观锁版本（PUT 使用 If-Match: "v{version}"） */
  private Integer version;
}
