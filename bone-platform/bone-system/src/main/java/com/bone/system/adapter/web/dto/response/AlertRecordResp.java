package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警事件响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecordResp {
  private Long id;
  private Long alertRuleId;
  private String ruleName;
  private String metricName;
  private double actualValue;
  private double threshold;
  private String alertLevel;
  private String message;
  private String status;
  private Instant createdAt;
  private Instant resolveTime;
}
