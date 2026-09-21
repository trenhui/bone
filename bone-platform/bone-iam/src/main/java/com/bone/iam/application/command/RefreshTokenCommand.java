package com.bone.iam.application.command;

import lombok.Data;

@Data
public class RefreshTokenCommand {
  private String refreshToken;
}
