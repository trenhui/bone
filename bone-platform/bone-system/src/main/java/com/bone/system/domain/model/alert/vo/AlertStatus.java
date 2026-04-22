package com.bone.system.domain.model.alert.vo;

public enum AlertStatus {
    TRIGGERED("触发"),
    RESOLVED("解决");

    private final String description;

    AlertStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
