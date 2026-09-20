package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateApplicationCommand {
  private Long id;
  private String name;
  private String description;
  private String icon;
  private Integer status;
}
