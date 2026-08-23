package com.bone.masterdata.application.command.cmd;

import lombok.Data;

@Data
public class CreateDataStandardCommand {
  private String entityCode;
  private String fieldCode;
  private Integer ruleType;
  private String pattern;
  private String refCode;
  private String description;
}
