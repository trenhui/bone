package com.bone.iam.domain.model.role.vo;

import com.bone.core.exception.DomainException;

public record RoleName(String value) {
    public RoleName {
        if (value == null || value.isBlank()) {
            throw new DomainException("角色名称不能为空");
        }
        if (value.length() < 2 || value.length() > 50) {
            throw new DomainException("角色名称长度必须在2-50个字符之间");
        }
    }

    public static RoleName of(String value) {
        return new RoleName(value);
    }
}