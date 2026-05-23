package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMetaFieldCommand {
  @NotNull private Long entityId;
  @NotBlank private String name;
  @NotBlank private String code;
  @NotBlank private String displayName;
  @NotBlank private String type;
}
