package com.bone.masterdata.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateDataQualityRuleReq {
  private String name;
  private String type;
  private String expression;
  private String severity;
  private String description;
}
