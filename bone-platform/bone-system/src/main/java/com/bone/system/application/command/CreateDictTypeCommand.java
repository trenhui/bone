package com.bone.system.application.command;

import lombok.Data;

/** 新建字典类型。{@code builtin} 仅平台（tenantId=0）可置真，租户侧由应用服务强制降级。 */
@Data
public class CreateDictTypeCommand {
  private String code;
  private String name;
  private String category;
  private String moduleCode;
  private String enumClass;
  private Integer maxDepth;

  /** SAP Domain 口径：值的技术类型（STRING/INT/DECIMAL/BOOLEAN）。 */
  private String valueType;

  /** 值格式正则。 */
  private String valueRegex;

  /** 层级编码分段，如 2,2,2（GB/T 2260 风格：由编码前缀推导父级）。 */
  private String codeSegments;

  private String description;
  private Integer sort;
  private Integer status;
  private Boolean builtin;
}
