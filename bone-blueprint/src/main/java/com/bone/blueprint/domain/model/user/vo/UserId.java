package com.bone.blueprint.domain.model.user.vo;

import lombok.Getter;

import java.util.UUID;

/**
 * 用户ID值对象
 * <p>
 * 使用UUID作为业务ID
 * </p>
 */
@Getter
public class UserId {
    private final String value;

    private UserId(String value) {
        this.value = value;
    }

    public static UserId of(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return value.equals(userId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}