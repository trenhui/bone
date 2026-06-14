package com.bone.system.application.command.cmd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 解决告警命令 */
@Data
public class ResolveAlertCommand {
  @NotNull(message = "告警事件ID不能为空")
  private Long id;
}
