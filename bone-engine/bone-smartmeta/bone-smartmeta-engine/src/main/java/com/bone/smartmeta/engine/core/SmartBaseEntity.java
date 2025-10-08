package com.bone.smartmeta.engine.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 所有实体的基类，提供通用属性和方法
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class SmartBaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "system_modstamp")
    private LocalDateTime systemModstamp;

    @Column(name = "is_deleted", columnDefinition = "boolean default false")
    private Boolean isDeleted = false;

    // 存储额外的动态字段
    @Transient
    private Map<String, Object> extraFields = new HashMap<>();

    /**
     * 获取字段值，优先从实体属性获取，其次从额外字段获取
     */
    public Object getField(String fieldName) {
        try {
            var field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(this);
        } catch (Exception e) {
            return extraFields.get(fieldName);
        }
    }

    /**
     * 设置字段值，优先设置到实体属性，其次设置到额外字段
     */
    public void setField(String fieldName, Object value) {
        try {
            var field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(this, value);
        } catch (Exception e) {
            extraFields.put(fieldName, value);
        }
    }

    /**
     * 检查是否存在指定字段
     */
    public boolean hasField(String fieldName) {
        try {
            this.getClass().getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return extraFields.containsKey(fieldName);
        }
    }
}
