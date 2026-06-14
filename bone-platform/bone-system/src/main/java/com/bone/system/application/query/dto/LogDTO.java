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
public class LogDTO {
  private Long id;
  private String logLevel;
  private String serviceName;
  private String content;
  private String traceId;
  private LocalDateTime createdAt;
}
