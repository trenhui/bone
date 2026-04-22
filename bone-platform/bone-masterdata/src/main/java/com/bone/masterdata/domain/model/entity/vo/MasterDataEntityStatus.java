package com.bone.masterdata.domain.model.entity.vo;

public enum MasterDataEntityStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布");

    private final String description;

    MasterDataEntityStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}