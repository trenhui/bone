package com.bone.system.application.command;

import lombok.Data;

@Data
public class CreateDictCommand {
  private String type;
  private String typeName;
  private String code;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
}
