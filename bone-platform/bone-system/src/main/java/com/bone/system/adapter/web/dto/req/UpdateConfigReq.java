package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 更新配置请求 */
@Data
public class UpdateConfigReq {
  @NotNull(message = "配置ID不能为空")
  private Long id;

  private String configValue;

  private String description;
}
