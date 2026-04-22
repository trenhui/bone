package com.bone.iam.domain.model.audit.vo;

import com.bone.core.exception.DomainException;

public record AuditLogId(Long value) {
    public AuditLogId {
        if (value == null || value <= 0) {
            throw new DomainException("审计日志ID必须大于0");
        }
    }

    public static AuditLogId of(Long value) {
        return new AuditLogId(value);
    }
}