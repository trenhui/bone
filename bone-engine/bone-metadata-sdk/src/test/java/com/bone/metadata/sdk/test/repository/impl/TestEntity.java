package com.bone.metadata.sdk.test.repository.impl;

import java.io.Serializable;

/**
 * 测试用实体类
 */
public class TestEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private Integer status;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
}