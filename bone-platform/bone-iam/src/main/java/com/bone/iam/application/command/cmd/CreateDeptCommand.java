package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class CreateDeptCommand {
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
  private Long tenantId;
}
