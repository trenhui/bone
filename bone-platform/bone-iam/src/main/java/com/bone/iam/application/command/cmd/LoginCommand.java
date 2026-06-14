package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class LoginCommand {
  private String username;
  private String password;

  /** 调用方客户端 IP（由 adapter 层从 HttpServletRequest 提取，登录成功记入审计）。 */
  private String clientIp;
}
