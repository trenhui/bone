package com.bone.system.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConfigCommand {
  @NotBlank(message = "配置键不能为空")
  private String configKey;

  @NotNull(message = "配置值不能为空")
  private String configValue;

  private String description;

  @NotBlank(message = "配置类型不能为空")
  private String configType;

  private boolean encrypted;
}
