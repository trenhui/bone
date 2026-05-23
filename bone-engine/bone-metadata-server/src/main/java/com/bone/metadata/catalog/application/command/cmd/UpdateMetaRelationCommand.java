package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMetaRelationCommand {
  @NotBlank private String name;
  @NotBlank private String type;
  private Long sourceFieldId;
  private Long targetFieldId;
  private String foreignKeyField;
  private Boolean required;
  private String cascadeType;
}
