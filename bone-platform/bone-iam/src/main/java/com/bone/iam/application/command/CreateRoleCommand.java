package com.bone.iam.application.command;

import lombok.Data;

@Data
public class CreateRoleCommand {
  private String name;
  private String code;
  private String description;
  private Long tenantId;
}
