package com.bone.blueprint.domain.model.user.vo;

import lombok.Getter;

/**
 * 密码值对象
 * <p>
 * 包含密码的验证和加密逻辑
 * </p>
 */
@Getter
public class Password {
    private final String value;

    private Password(String value) {
        this.value = value;
    }

    public static Password of(String rawPassword, com.bone.blueprint.domain.gateway.PasswordEncoder encoder) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        String encryptedPassword = encoder.encode(rawPassword);
        return new Password(encryptedPassword);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Password password = (Password) o;
        return value.equals(password.value);
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