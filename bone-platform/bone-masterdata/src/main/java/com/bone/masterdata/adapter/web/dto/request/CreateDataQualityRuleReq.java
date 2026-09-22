package com.bone.masterdata.adapter.web.dto.request;

import lombok.Data;

@Data
public class CreateDataQualityRuleReq {
  private Long masterDataEntityId;
  private Long masterDataFieldId;
  private String name;

  /** 与前端 CreateDataQualityRuleReq 及 DataQualityRuleDTO 同名，勿改回 ruleType/ruleConfig。 */
  private String type;

  private String expression;
  private String severity;
  private String description;
}
