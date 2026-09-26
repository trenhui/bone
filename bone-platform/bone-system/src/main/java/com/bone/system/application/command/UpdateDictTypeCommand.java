package com.bone.system.application.command;

import lombok.Data;

/** 更新字典类型；{@code code} 是业务键，不可改。 */
@Data
public class UpdateDictTypeCommand {
  private Long id;
  private String name;
  private String moduleCode;
  private String enumClass;
  private Integer maxDepth;
  private String valueType;
  private String valueRegex;
  private String codeSegments;
  private String description;
  private Integer sort;
  private Integer status;
}
