package com.bone.system.adapter.web.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日志响应
 */
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
    private LocalDateTime createTime;
}
