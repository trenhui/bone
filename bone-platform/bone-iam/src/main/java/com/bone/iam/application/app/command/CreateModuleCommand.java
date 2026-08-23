package com.bone.iam.application.app.command;

import lombok.Data;

@Data
public class CreateModuleCommand {
  private Long appId;
  private String name;
  private String code;
  private String description;
}
