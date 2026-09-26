package com.bone.metadata.catalog.application.command.cmd;

import lombok.Getter;
import lombok.Setter;

/** 从平台模板实例化租户实体的命令（G3/ADR-0031，UC-MT2）。 */
@Getter
@Setter
public class InstantiateFromTemplateCommand {

  private String name;
  private String code;
  private String displayName;
  private String tableName;
  private String description;
  private Long moduleId;
  private String icon;
  private Integer deliveryMode;
}
