package com.bone.system.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 更新字典类型请求（编码不可改）。 */
@Data
public class UpdateDictTypeReq {

  @NotBlank(message = "值域名称不能为空")
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
