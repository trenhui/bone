package com.bone.iam.domain.model.user.vo;

public enum UserStatus {
    ENABLED("启用"),
    DISABLED("禁用");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}