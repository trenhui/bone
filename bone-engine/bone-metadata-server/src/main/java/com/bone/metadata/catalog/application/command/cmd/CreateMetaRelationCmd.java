package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMetaRelationCmd {
  @NotBlank private String name;
  @NotNull private Long sourceEntityId;
  @NotNull private Long targetEntityId;
  @NotBlank private String type;
  private Long sourceFieldId;
  private Long targetFieldId;
  private String foreignKeyField;
  private Boolean required;
  private String cascadeType;
}
