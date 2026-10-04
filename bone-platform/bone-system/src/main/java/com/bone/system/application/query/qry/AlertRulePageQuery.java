package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class AlertRulePageQuery {
  private String keyword;
  private String alertLevel;
  private Boolean enabled;
  private int page = 1;
  private int size = 10;
}
