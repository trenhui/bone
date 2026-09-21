package com.bone.system.application.command;

import lombok.Data;

@Data
public class CreateScheduleTaskCommand {
  private String name;
  private String cron;
  private String handler;
  private String status;
}
