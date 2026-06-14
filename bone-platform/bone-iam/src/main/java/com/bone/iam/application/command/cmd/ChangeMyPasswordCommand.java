package com.bone.iam.application.command.cmd;

import lombok.Data;

/** 自助改密：必须提供旧密码 + 新密码（新密码经弱口令策略 + BCrypt 重哈希）。 */
@Data
public class ChangeMyPasswordCommand {

  private Long accountId;
  private String oldPassword;
  private String newPassword;
}
