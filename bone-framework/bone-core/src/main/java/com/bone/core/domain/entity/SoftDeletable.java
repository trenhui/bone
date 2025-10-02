package com.bone.core.domain.entity;

/**
 * 逻辑删除接口
 */
public interface SoftDeletable {
    Boolean getDeleted();
    void setDeleted(Boolean deleted);
}