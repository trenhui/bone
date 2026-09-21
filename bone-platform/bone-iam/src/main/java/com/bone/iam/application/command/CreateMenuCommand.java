package com.bone.iam.application.command;

import lombok.Data;

@Data
public class CreateMenuCommand {
  private String name;
  private Long parentId;
  private String path;
  private String icon;
  private Integer orderNo;
  private String permission;
  private Integer type;
  private Long tenantId;
}
