package com.bone.iam.domain.model.permission.vo;

import com.bone.core.exception.DomainException;

import java.util.UUID;

public record PermissionId(String value) {
    public PermissionId {
        if (value == null || value.isEmpty()) {
            throw new DomainException("权限ID不能为空");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new DomainException("权限ID必须是有效的UUID格式");
        }
    }

    public static PermissionId of(String value) {
        return new PermissionId(value);
    }
}