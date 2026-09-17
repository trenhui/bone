package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 告警事件分页查询请求 */
@Data
public class AlertRecordPageReq {
  private Long alertRuleId;
  private String alertLevel;
  private String status;
  private int pageNum = 1;
  private int pageSize = 10;
}
