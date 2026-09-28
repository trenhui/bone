package com.bone.system.application.query.dto;

import com.bone.system.domain.model.log.SystemLog;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 系统日志的应用投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogDto {
  private Long id;
  private String logLevel;
  private String serviceName;
  private String content;
  private String traceId;
  private LocalDateTime createdAt;

  /** 聚合 → 应用投影。 */
  public static LogDto from(SystemLog log) {
    return LogDto.builder()
        .id(log.getId())
        .logLevel(log.getLevel().name())
        .serviceName(log.getService())
        .content(log.getContent())
        .traceId(log.getTraceId())
        .createdAt(log.getCreatedAt())
        .build();
  }
}
