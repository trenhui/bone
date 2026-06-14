package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 创建配置请求 */
@Data
public class CreateConfigReq {
  @NotBlank(message = "配置键不能为空")
  private String configKey;

  @NotNull(message = "配置值不能为空")
  private String configValue;

  private String description;

  @NotBlank(message = "配置类型不能为空")
  private String configType;

  private boolean encrypted;
}
