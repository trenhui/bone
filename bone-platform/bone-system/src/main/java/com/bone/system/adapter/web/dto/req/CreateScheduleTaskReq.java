package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateScheduleTaskReq {
  @NotBlank(message = "任务名称不能为空")
  private String name;

  @NotBlank(message = "CRON 表达式不能为空")
  private String cron;

  @NotBlank(message = "处理器 bean 不能为空")
  private String handler;

  private String status = "DISABLED";
}
