package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateRoleCommand {
  private Long id;
  private String name;
  private String description;
}
