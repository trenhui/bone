package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMetaEntityCommand {
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  private String description;
  @NotBlank private String tableName;
  private Integer type;

  /** 0-GENERATIVE（默认） 1-RUNTIME */
  private Integer deliveryMode;

  private String icon;
}
