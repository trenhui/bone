package com.bone.iam.domain.model.user.vo;

import com.bone.core.exception.DomainException;

import java.util.regex.Pattern;

public record Email(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainException("邮箱不能为空");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new DomainException("邮箱格式不正确");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }
}