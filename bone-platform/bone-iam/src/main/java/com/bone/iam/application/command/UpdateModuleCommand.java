package com.bone.iam.application.command;

import lombok.Data;

@Data
public class UpdateModuleCommand {
  private Long id;
  private String name;
  private String description;
  private Integer status;
}
