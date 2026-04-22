package com.bone.system.adapter.web.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则响应
 */
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
