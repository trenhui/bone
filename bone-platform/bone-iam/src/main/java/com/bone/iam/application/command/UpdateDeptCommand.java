package com.bone.iam.application.command;

import lombok.Data;

@Data
public class UpdateDeptCommand {
  private Long id;
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
}
