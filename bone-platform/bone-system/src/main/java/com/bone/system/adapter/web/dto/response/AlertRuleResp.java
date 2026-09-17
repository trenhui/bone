package com.bone.system.adapter.web.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警规则响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleResp {
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
