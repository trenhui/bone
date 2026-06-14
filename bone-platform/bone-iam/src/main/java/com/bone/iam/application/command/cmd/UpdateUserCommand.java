package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateUserCommand {
  private String id;
  private String email;
  private Long[] roleIds;
}
