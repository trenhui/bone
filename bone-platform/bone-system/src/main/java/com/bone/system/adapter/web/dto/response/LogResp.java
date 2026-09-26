package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 日志响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogResp {
  private Long id;
  private String logLevel;
  private String serviceName;
  private String content;
  private String traceId;
  private Instant createdAt;
}
