package com.bone.iam.domain.model.permission.vo;

import com.bone.core.exception.DomainException;

public record PermissionCode(String value) {
    public PermissionCode {
        if (value == null || value.isBlank()) {
            throw new DomainException("权限代码不能为空");
        }
        if (value.length() < 3 || value.length() > 50) {
            throw new DomainException("权限代码长度必须在3-50个字符之间");
        }
        if (!value.matches("^[a-zA-Z0-9_]+$") && !value.matches("^[a-zA-Z0-9_:]+$")) {
            throw new DomainException("权限代码只能包含字母、数字、下划线和冒号");
        }
    }

    public static PermissionCode of(String value) {
        return new PermissionCode(value);
    }
}