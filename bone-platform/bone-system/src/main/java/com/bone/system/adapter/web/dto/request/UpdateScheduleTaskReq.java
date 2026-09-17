package com.bone.system.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateScheduleTaskReq {
  @NotBlank(message = "任务名称不能为空")
  private String name;

  @NotBlank(message = "CRON 表达式不能为空")
  private String cron;

  @NotBlank(message = "处理器 bean 不能为空")
  private String handler;
}
