package com.bone.masterdata.application.command;

import lombok.Data;

@Data
public class UpdateDataStandardCommand {
  private Long id;
  private Integer ruleType;
  private String pattern;
  private String refCode;
  private String description;
}
