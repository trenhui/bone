package com.bone.system.application.query.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDTO {
  private Long id;
  private String configKey;
  private String configValue;
  private String description;
  private String configType;
  private boolean encrypted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
