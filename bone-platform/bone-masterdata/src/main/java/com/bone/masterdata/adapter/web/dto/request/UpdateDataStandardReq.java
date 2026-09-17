package com.bone.masterdata.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateDataStandardReq {
  private Integer ruleType;
  private String pattern;
  private String refCode;
  private String description;
}
