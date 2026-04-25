package com.bone.iam.application.query.dto;

import com.bone.iam.domain.audit.vo.OperationType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogDTO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private OperationType operation;
    private String resourceId;
    private String resourceType;
    private String ip;
    private String userAgent;
    private String parameters;
    private String result;
    private Integer duration;
    private LocalDateTime createTime;
}