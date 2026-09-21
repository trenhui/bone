package com.bone.iam.application.command;

import lombok.Data;

@Data
public class UpdateUserCommand {
  private String id;
  private String email;
  private Long[] roleIds;
}
