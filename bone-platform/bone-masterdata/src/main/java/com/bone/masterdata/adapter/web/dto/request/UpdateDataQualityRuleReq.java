package com.bone.masterdata.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateDataQualityRuleReq {
  private String name;
  private String ruleType;
  private String ruleConfig;
  private String severity;
  private String description;
}
