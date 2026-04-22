package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建告警规则请求
 */
@Data
public class CreateAlertRuleReq {
    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "指标名称不能为空")
    private String metricName;

    @NotNull(message = "阈值不能为空")
    private Double threshold;

    @NotBlank(message = "告警级别不能为空")
    private String alertLevel;

    private List<String> notificationChannels;
}
