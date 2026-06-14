package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class CreateTenantCommand {
  private String name;
  private String code;
  private Integer level;
  private String adminEmail;
}
