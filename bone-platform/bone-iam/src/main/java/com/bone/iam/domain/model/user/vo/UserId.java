package com.bone.iam.domain.model.user.vo;

import com.bone.core.exception.DomainException;

import java.util.UUID;

public record UserId(String value) {
    public UserId {
        if (value == null || value.isEmpty()) {
            throw new DomainException("用户ID不能为空");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new DomainException("用户ID必须是有效的UUID格式");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }
}