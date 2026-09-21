package com.bone.system.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateLogCommand {
  @NotBlank(message = "日志级别不能为空")
  private String logLevel;

  @NotBlank(message = "服务名称不能为空")
  private String serviceName;

  @NotBlank(message = "日志内容不能为空")
  private String content;

  private String traceId;
}
