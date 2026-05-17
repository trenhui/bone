package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

@Data
public class MetaRelationDTO {
  private Long id;
  private String name;
  private Long sourceEntityId;
  private Long targetEntityId;
  private String type;
  private Long sourceFieldId;
  private Long targetFieldId;
  private String foreignKeyField;
  private Boolean required;
  private String cascadeType;
}
