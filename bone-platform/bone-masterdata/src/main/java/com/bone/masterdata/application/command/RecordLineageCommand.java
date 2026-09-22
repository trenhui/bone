package com.bone.masterdata.application.command;

import lombok.Data;

@Data
public class RecordLineageCommand {
  private String sourceEntity;
  private String sourceField;
  private String transformType;
  private String targetEntity;
  private String targetField;
  private String schemaName;
}
