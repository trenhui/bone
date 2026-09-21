package com.bone.iam.application.command;

import lombok.Data;

@Data
public class ResetPasswordCommand {
  private Long id;
  private String newPassword;
}
