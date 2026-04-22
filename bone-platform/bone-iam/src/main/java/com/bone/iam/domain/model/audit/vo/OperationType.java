package com.bone.iam.domain.model.audit.vo;

public enum OperationType {
    LOGIN("登录"),
    LOGOUT("登出"),
    CREATE("创建"),
    UPDATE("更新"),
    DELETE("删除"),
    QUERY("查询"),
    ASSIGN("分配"),
    IMPORT("导入"),
    EXPORT("导出"),
    ENABLE("启用"),
    DISABLE("禁用"),
    RESET_PASSWORD("重置密码");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}