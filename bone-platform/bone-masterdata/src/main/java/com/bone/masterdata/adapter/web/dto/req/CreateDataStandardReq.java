package com.bone.masterdata.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDataStandardReq {
  @NotBlank(message = "实体编码不能为空")
  private String entityCode;

  @NotBlank(message = "字段编码不能为空")
  private String fieldCode;

  private Integer ruleType;
  private String pattern;
  private String refCode;
  private String description;
}
