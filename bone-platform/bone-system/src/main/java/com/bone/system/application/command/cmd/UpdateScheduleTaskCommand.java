package com.bone.system.application.command.cmd;

import lombok.Data;

@Data
public class UpdateScheduleTaskCommand {
  private Long id;
  private String name;
  private String cron;
  private String handler;
}
