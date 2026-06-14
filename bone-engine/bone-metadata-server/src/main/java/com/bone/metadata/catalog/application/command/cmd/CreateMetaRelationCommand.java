package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMetaRelationCommand {
  @NotBlank private String name;
  @NotNull private Long sourceEntityId;
  @NotNull private Long targetEntityId;
  @NotBlank private String type;

  /** relationType 别名，兼容前端传参 */
  private String relationType;

  private Long sourceFieldId;
  private Long targetFieldId;
  private String foreignKeyField;
  private Boolean required;
  private String cascadeType;
}
