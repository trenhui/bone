package com.bone.iam.application.command;

import lombok.Data;

@Data
public class UpdateMenuCommand {
  private Long id;
  private String name;
  private Long parentId;
  private String path;
  private String icon;
  private Integer orderNo;
  private String permission;
  private Integer type;
}
