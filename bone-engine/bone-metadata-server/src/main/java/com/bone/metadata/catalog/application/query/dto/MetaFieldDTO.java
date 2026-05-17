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
}
