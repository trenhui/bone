package com.bone.masterdata.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateDataQualityRuleReq {
  private Long masterDataEntityId;
  private Long masterDataFieldId;
  private String name;
  private String ruleType;
  private String ruleConfig;
  private String severity;
  private String description;
}
