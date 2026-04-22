package com.bone.masterdata.domain.model.quality.vo;

public enum RuleSeverity {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    CRITICAL("严重");

    private final String description;

    RuleSeverity(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}