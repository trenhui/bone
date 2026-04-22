package com.bone.system.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateAlertRuleCmd {
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
