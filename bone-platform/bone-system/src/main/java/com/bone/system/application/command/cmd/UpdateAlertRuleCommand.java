package com.bone.system.application.command.cmd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateAlertRuleCommand {
    @NotNull(message = "规则ID不能为空")
    private Long id;

    private String name;

    private String description;

    private Double threshold;

    private String alertLevel;

    private List<String> notificationChannels;
}
