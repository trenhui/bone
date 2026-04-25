package com.bone.iam.domain.account.vo;

public record Email(String value) {
    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!value.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
