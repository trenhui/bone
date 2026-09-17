package com.bone.masterdata.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecordLineageReq {
  @NotBlank(message = "来源实体不能为空")
  private String sourceEntity;

  private String sourceField;
  private String transformType;

  @NotBlank(message = "目标实体不能为空")
  private String targetEntity;

  private String targetField;
  private String schemaName;
}
