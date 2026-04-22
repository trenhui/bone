package com.bone.iam.domain.model.role.vo;

import com.bone.core.exception.DomainException;

import java.util.UUID;

public record RoleId(String value) {
    public RoleId {
        if (value == null || value.isEmpty()) {
            throw new DomainException("角色ID不能为空");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new DomainException("角色ID必须是有效的UUID格式");
        }
    }

    public static RoleId of(String value) {
        return new RoleId(value);
    }
}