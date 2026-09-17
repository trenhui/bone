package com.bone.system.adapter.web.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 配置响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigResp {
  private Long id;
  private String configKey;
  private String configValue;
  private String description;
  private String configType;
  private boolean encrypted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
