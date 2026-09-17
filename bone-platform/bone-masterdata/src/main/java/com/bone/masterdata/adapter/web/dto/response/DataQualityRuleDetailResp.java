package com.bone.masterdata.adapter.web.dto.response;

import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataQualityRuleDetailResp {
  private Long id;
  private Long masterDataEntityId;
  private Long masterDataFieldId;
  private String name;
  private String ruleType;
  private String ruleConfig;
  private String severity;
  private String status;
  private String description;
  private Date createdAt;
  private Date updatedAt;
}
