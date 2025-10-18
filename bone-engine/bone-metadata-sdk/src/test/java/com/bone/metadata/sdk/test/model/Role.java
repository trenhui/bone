package com.bone.metadata.sdk.test.model;

import com.bone.metadata.sdk.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Role implements Entity<Long> {
    private Long id;
    private String name;
    private String code;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}