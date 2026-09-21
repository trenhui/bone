package com.bone.system.application.query.dto;

import com.bone.system.domain.alert.AlertRule;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警规则的应用投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDto {
  private Long id;
  private String name;
  private String description;
  private String metricName;
  private double threshold;
  private String alertLevel;
  private List<String> notificationChannels;
  private boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 聚合 → 应用投影（值对象拆标量为协议可序列化的形态）。 */
  public static AlertRuleDto from(AlertRule rule) {
    return AlertRuleDto.builder()
        .id(rule.getId())
        .name(rule.getName())
        .description(rule.getDescription())
        .metricName(rule.getMetricName().value())
        .threshold(rule.getThreshold().value())
        .alertLevel(rule.getAlertLevel().name())
        .notificationChannels(rule.getNotificationChannels())
        .enabled(rule.isEnabled())
        .createdAt(rule.getCreatedAt())
        .updatedAt(rule.getUpdatedAt())
        .build();
  }
}
