package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMetaFieldCommand {
  private String name;
  @NotBlank private String displayName;
  @NotBlank private String type;
  private Integer length;
  private Boolean required;
  private Boolean unique;
  private String defaultValue;
  private String comment;
  private Integer sortOrder;
}
