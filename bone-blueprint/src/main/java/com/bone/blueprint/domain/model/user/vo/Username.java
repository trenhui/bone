package com.bone.blueprint.domain.model.user.vo;

import lombok.Getter;

/**
 * 用户名值对象
 * <p>
 * 包含用户名的验证逻辑
 * </p>
 */
@Getter
public class Username {
    private final String value;

    private Username(String value) {
        this.value = value;
    }

    public static Username of(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (value.length() < 3 || value.length() > 20) {
            throw new IllegalArgumentException("用户名长度必须在3-20之间");
        }
        if (!value.matches("^[a-zA-Z0-9_]+$") ) {
            throw new IllegalArgumentException("用户名只能包含字母、数字和下划线");
        }
        return new Username(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Username username = (Username) o;
        return value.equals(username.value);
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