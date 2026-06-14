package com.bone.system.application.query.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDTO {
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
}
