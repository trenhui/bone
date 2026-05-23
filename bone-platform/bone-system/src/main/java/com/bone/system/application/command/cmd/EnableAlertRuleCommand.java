package com.bone.system.application.command.cmd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用告警规则命令
 */
@Data
public class EnableAlertRuleCommand {
    @NotNull(message = "规则ID不能为空")
    private Long id;
}
