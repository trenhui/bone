package com.bone.iam.application.command;

import lombok.Data;

@Data
public class UpdateAccountCommand {
  private Long id;
  private String email;
  private String phone;
  private String realName;
  private Integer status;

  /** 归属部门（主部门）；PUT 为全量覆盖语义，null=不变更（保留原归属部门，不可清空），不可为空。 */
  private Long deptId;

  /** 直属主管：null = 不变更，0 = 清空（无主管），其他 = 设为该账户。 */
  private Long managerId;

  private Long[] roleIds;
}
