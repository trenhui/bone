package com.bone.iam.application.app.command;

import lombok.Data;

@Data
public class CreateApplicationCommand {
  private String name;
  private String code;
  private String description;
  private String icon;
}
