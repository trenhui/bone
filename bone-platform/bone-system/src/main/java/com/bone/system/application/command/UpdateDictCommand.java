package com.bone.system.application.command;

import lombok.Data;

@Data
public class UpdateDictCommand {
  private Long id;
  private String typeName;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
}
