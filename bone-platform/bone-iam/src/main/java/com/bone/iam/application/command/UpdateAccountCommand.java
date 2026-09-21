package com.bone.iam.application.command;

import lombok.Data;

@Data
public class UpdateAccountCommand {
  private Long id;
  private String email;
  private String phone;
  private String realName;
  private Integer status;
  private Long[] roleIds;
}
