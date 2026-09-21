package com.bone.system.application.query.dto;

import com.bone.system.domain.alert.AlertRecord;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警记录的应用投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecordDto {
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

  /** 聚合 → 应用投影。 */
  public static AlertRecordDto from(AlertRecord record) {
    return AlertRecordDto.builder()
        .id(record.getId())
        .alertRuleId(record.getAlertRuleId())
        .ruleName(record.getRuleName())
        .metricName(record.getMetricName())
        .actualValue(record.getActualValue())
        .threshold(record.getThreshold())
        .alertLevel(record.getAlertLevel().name())
        .message(record.getMessage())
        .status(record.getStatus().name())
        .createdAt(record.getCreatedAt())
        .resolveTime(record.getResolveTime())
        .build();
  }
}
