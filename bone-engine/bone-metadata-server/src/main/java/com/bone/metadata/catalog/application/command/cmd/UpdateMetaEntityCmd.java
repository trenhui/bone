package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMetaEntityCmd {
  @NotBlank private String name;
  @NotBlank private String displayName;
  private String description;
  @NotBlank private String tableName;
  private Integer sortOrder;
  private String icon;
}
