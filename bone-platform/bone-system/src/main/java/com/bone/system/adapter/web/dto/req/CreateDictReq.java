package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDictReq {
  @NotBlank(message = "字典类型不能为空")
  private String type;

  private String typeName;

  @NotBlank(message = "字典编码不能为空")
  private String code;

  @NotBlank(message = "字典标签不能为空")
  private String label;

  private String value;
  private Integer sort;
  private Integer status;
}
