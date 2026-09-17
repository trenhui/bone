package com.bone.system.application.query.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecordDTO {
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
