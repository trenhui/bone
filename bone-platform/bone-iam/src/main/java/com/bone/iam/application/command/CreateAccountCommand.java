package com.bone.iam.application.command;

import lombok.Data;

@Data
public class CreateAccountCommand {
  private String username;
  private String password;
  private String email;
  private String phone;
  private String realName;
  private Long tenantId;

  /** 归属部门（主部门）；由应用服务校验其存在且属于同一租户。 */
  private Long deptId;

  private Long[] roleIds;
}
