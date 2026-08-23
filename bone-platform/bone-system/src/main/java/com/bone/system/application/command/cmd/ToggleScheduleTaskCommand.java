package com.bone.system.application.command.cmd;

import lombok.Data;

@Data
public class ToggleScheduleTaskCommand {
  private Long id;
  private boolean enabled;
}
