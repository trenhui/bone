package com.bone.smartmeta.engine.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.bone.core.domain.entity.Entity;

/**
 * 智能实体基类
 */
public abstract class SmartBaseEntity extends Entity<String> implements Serializable {
    private String id;
    private String name;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
    
    // Getter and Setter methods
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    
    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
    
    public LocalDateTime getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(LocalDateTime lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }
    
    /**
     * 获取字段值
     */
    public Object getField(String fieldName) {
        // 简化实现，实际应该通过反射或字段映射获取值
        return null;
    }
    
    /**
     * 设置字段值
     */
    public void setField(String fieldName, Object value) {
        // 简化实现，实际应该通过反射或字段映射设置值
    }
}