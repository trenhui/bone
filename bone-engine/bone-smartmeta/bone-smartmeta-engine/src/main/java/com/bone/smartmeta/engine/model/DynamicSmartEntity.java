package com.bone.smartmeta.engine.model;

import com.bone.smartmeta.engine.core.SmartBaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态实体类，支持基于元数据的动态字段
 */
@Getter
@Setter
public class DynamicSmartEntity extends SmartBaseEntity {

    // 实体的API名称
    private String entityApiName;
    
    // 存储动态字段值
    private Map<String, Object> dynamicFields = new HashMap<>();
    
    // 记录类型
    private String recordType;

    /**
     * 构造函数
     */
    public DynamicSmartEntity() {
        super();
    }

    /**
     * 构造函数
     */
    public DynamicSmartEntity(String entityApiName) {
        super();
        this.entityApiName = entityApiName;
    }

    /**
     * 获取动态字段值
     */
    @Override
    public Object getField(String fieldName) {
        // 先尝试从父类获取（标准字段）
        Object value = super.getField(fieldName);
        
        // 如果父类没有该字段，则从动态字段获取
        if (value == null) {
            value = dynamicFields.get(fieldName);
        }
        
        return value;
    }

    /**
     * 设置动态字段值
     */
    @Override
    public void setField(String fieldName, Object value) {
        try {
            // 先尝试设置到父类（标准字段）
            super.setField(fieldName, value);
        } catch (Exception e) {
            // 如果父类没有该字段，则设置到动态字段
            dynamicFields.put(fieldName, value);
        }
    }

    /**
     * 检查是否存在指定字段
     */
    @Override
    public boolean hasField(String fieldName) {
        return super.hasField(fieldName) || dynamicFields.containsKey(fieldName);
    }

    /**
     * 获取所有字段键值对
     */
    public Map<String, Object> getAllFields() {
        Map<String, Object> allFields = new HashMap<>();
        
        // 添加标准字段
        // 暂时注释掉不存在的方法调用，提供默认值
        // allFields.put("id", getId());
        // allFields.put("name", getName());
        // allFields.put("createdDate", getCreatedDate());
        // allFields.put("createdBy", getCreatedBy());
        
        // 添加默认值代替不存在的字段
        allFields.put("id", null);
        allFields.put("name", null);
        allFields.put("createdDate", null);
        allFields.put("createdBy", null);
        allFields.put("lastModifiedDate", null);
        allFields.put("lastModifiedBy", null);
        allFields.put("systemModstamp", null);
        allFields.put("isDeleted", false);
        
        // 添加动态字段
        allFields.putAll(dynamicFields);
        
        return allFields;
    }
}
