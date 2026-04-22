package com.bone.masterdata.domain.model.record.vo;

public enum MasterDataRecordStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    ARCHIVED("已归档");

    private final String description;

    MasterDataRecordStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}