package com.bone.system.application.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createTime;
}
