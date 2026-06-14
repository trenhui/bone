package com.bone.system.adapter.web.dto.resp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警事件响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventResp {
  private Long id;
  private Long alertRuleId;
  private String ruleName;
  private String metricName;
  private double actualValue;
  private double threshold;
  private String alertLevel;
  private String message;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime resolveTime;
}
