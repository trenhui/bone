package com.bone.iam.domain.model.permission.vo;

public enum PermissionType {
    MENU("菜单"),
    OPERATION("操作"),
    DATA("数据");

    private final String description;

    PermissionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}