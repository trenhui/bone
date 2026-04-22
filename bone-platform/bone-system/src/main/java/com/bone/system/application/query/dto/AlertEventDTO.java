package com.bone.system.application.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventDTO {
    private Long id;
    private Long alertRuleId;
    private String ruleName;
    private String metricName;
    private double actualValue;
    private double threshold;
    private String alertLevel;
    private String message;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime resolveTime;
}
