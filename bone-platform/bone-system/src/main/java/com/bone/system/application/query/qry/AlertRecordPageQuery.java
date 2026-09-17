package com.bone.system.application.query.qry;

import lombok.Data;

/** 告警事件分页查询 */
@Data
public class AlertRecordPageQuery {
  private Long alertRuleId;
  private String alertLevel;
  private String status;
  private int pageNum = 1;
  private int pageSize = 10;
}
