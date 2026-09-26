package com.bone.system.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建字典类型请求。 */
@Data
public class CreateDictTypeReq {

  @NotBlank(message = "值域编码不能为空")
  private String code;

  @NotBlank(message = "值域名称不能为空")
  private String name;

  /** ENUM / LIST / CASCADE，缺省 LIST。 */
  private String category;

  private String moduleCode;

  /** category=ENUM 时必填。 */
  private String enumClass;

  /** category=CASCADE 时可选，0 表示不限。 */
  private Integer maxDepth;

  /** 值的技术类型（SAP Domain 口径）STRING / INT / DECIMAL / BOOLEAN。 */
  private String valueType;

  /** 值格式正则。 */
  private String valueRegex;

  /** 层级编码分段，如 2,2,2（GB/T 2260 风格）。 */
  private String codeSegments;

  private String description;
  private Integer sort;
  private Integer status;
  private Boolean builtin;
}
