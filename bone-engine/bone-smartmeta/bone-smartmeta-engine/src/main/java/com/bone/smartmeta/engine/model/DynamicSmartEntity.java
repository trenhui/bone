package com.bone.smartmeta.engine.model;

import com.bone.smartmeta.engine.core.SmartBaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 动态智能实体类
 * 用于在运行时动态表示实体实例，支持灵活的数据访问和操作
 */
@Getter
@Setter
public class DynamicSmartEntity extends SmartBaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 实体基本信息
    private String entityType;
    private String entityApiName;
    
    // 存储动态字段值
    private Map<String, Object> dynamicFields = new HashMap<>();
    
    // 记录类型
    private String recordType;
    
    // 计算字段值缓存
    private transient Map<String, Object> calculatedFieldValues;
    
    // 虚拟字段值缓存
    private transient Map<String, Object> virtualFieldValues;
    
    // 字段变更跟踪
    private transient Map<String, Object> originalValues;
    
    // 实体状态信息
    private boolean newEntity = false;
    private boolean deleted = false;
    private boolean modified = false;
    
    // setter方法
    public void setDynamicFields(Map<String, Object> dynamicFields) {
        this.dynamicFields = dynamicFields;
    }
    
    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }
    
    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }
    
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    /**
     * 设置修改状态
     */
    public void setModified(boolean modified) {
        this.modified = modified;
    }

    /**
     * 构造函数
     */
    public DynamicSmartEntity() {
        super();
        initTransientFields();
    }

    /**
     * 构造函数
     */
    public DynamicSmartEntity(String entityApiName) {
        super();
        this.entityApiName = entityApiName;
        initTransientFields();
    }

    /**
     * 构造函数
     */
    public DynamicSmartEntity(String entityType, String entityApiName) {
        super();
        this.entityType = entityType;
        this.entityApiName = entityApiName;
        initTransientFields();
    }
    
    /**
     * 初始化瞬态字段
     */
    private void initTransientFields() {
        this.calculatedFieldValues = new HashMap<>();
        this.virtualFieldValues = new HashMap<>();
        this.originalValues = new HashMap<>();
    }

    /**
     * 获取动态字段值
     */
    @Override
    public Object getField(String fieldName) {
        // 先尝试从父类获取（标准字段）
        Object value = null;
        try {
            value = super.getField(fieldName);
        } catch (Exception ignored) {
            // 忽略父类没有的字段异常
        }
        
        // 如果父类没有该字段，则从动态字段获取
        if (value == null) {
            value = dynamicFields.get(fieldName);
        }
        
        // 如果是计算字段或虚拟字段，从缓存中获取
        if (value == null) {
            value = calculatedFieldValues.get(fieldName);
            if (value == null) {
                value = virtualFieldValues.get(fieldName);
            }
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
            Object oldValue = super.getField(fieldName);
            super.setField(fieldName, value);
            
            // 记录变更
            if (!originalValues.containsKey(fieldName)) {
                originalValues.put(fieldName, oldValue);
            }
            if (oldValue == null && value != null || oldValue != null && !oldValue.equals(value)) {
                this.modified = true;
                clearCalculatedFieldCache();
            }
        } catch (Exception e) {
            // 如果父类没有该字段，则设置到动态字段
            Object oldValue = dynamicFields.get(fieldName);
            dynamicFields.put(fieldName, value);
            
            // 记录变更
            if (!originalValues.containsKey(fieldName)) {
                originalValues.put(fieldName, oldValue);
            }
            if (oldValue == null && value != null || oldValue != null && !oldValue.equals(value)) {
                this.modified = true;
                clearCalculatedFieldCache();
            }
        }
    }
    
    /**
     * 批量设置字段值
     * @param values 字段值映射
     */
    public void setFields(Map<String, Object> values) {
        values.forEach(this::setField);
    }

    /**
     * 检查是否存在指定字段
     */
    @Override
    public boolean hasField(String fieldName) {
        try {
            // 检查父类是否有该字段
            super.getField(fieldName);
            return true;
        } catch (Exception e) {
            // 检查动态字段、计算字段或虚拟字段
            return dynamicFields.containsKey(fieldName) || 
                   calculatedFieldValues.containsKey(fieldName) || 
                   virtualFieldValues.containsKey(fieldName);
        }
    }
    
    /**
     * 获取所有动态字段名称
     * @return 动态字段名称集合
     */
    public Set<String> getDynamicFieldNames() {
        return dynamicFields.keySet();
    }
    
    /**
     * 获取动态字段值（直接从动态字段映射中获取）
     * @param fieldName 字段名称
     * @return 字段值
     */
    public Object getDynamicField(String fieldName) {
        return dynamicFields.get(fieldName);
    }
    
    /**
     * 设置动态字段值（直接设置到动态字段映射）
     * @param fieldName 字段名称
     * @param value 字段值
     */
    public void setDynamicField(String fieldName, Object value) {
        Object oldValue = dynamicFields.put(fieldName, value);
        
        // 记录变更
        if (!originalValues.containsKey(fieldName)) {
            originalValues.put(fieldName, oldValue);
        }
        if (oldValue == null && value != null || oldValue != null && !oldValue.equals(value)) {
            this.modified = true;
            clearCalculatedFieldCache();
        }
    }
    
    /**
     * 删除动态字段
     * @param fieldName 字段名称
     * @return 删除的字段值
     */
    public Object removeDynamicField(String fieldName) {
        Object oldValue = dynamicFields.get(fieldName);
        Object removed = dynamicFields.remove(fieldName);
        
        // 记录变更
        if (!originalValues.containsKey(fieldName)) {
            originalValues.put(fieldName, oldValue);
        }
        if (removed != null) {
            this.modified = true;
            clearCalculatedFieldCache();
        }
        return removed;
    }
    
    /**
     * 获取计算字段值
     * @param fieldName 计算字段名称
     * @return 计算字段值
     */
    public Object getCalculatedFieldValue(String fieldName) {
        return calculatedFieldValues.get(fieldName);
    }
    
    /**
     * 设置计算字段值
     * @param fieldName 计算字段名称
     * @param value 计算字段值
     */
    public void setCalculatedFieldValue(String fieldName, Object value) {
        calculatedFieldValues.put(fieldName, value);
    }
    
    /**
     * 清除计算字段缓存
     */
    public void clearCalculatedFieldCache() {
        calculatedFieldValues.clear();
    }
    
    /**
     * 获取虚拟字段值
     * @param fieldName 虚拟字段名称
     * @return 虚拟字段值
     */
    public Object getVirtualFieldValue(String fieldName) {
        return virtualFieldValues.get(fieldName);
    }
    
    /**
     * 设置虚拟字段值
     * @param fieldName 虚拟字段名称
     * @param value 虚拟字段值
     */
    public void setVirtualFieldValue(String fieldName, Object value) {
        virtualFieldValues.put(fieldName, value);
    }
    
    /**
     * 清除虚拟字段缓存
     */
    public void clearVirtualFieldCache() {
        virtualFieldValues.clear();
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCaches() {
        clearCalculatedFieldCache();
        clearVirtualFieldCache();
    }
    
    /**
     * 开始跟踪变更
     */
    public void startTrackingChanges() {
        originalValues.clear();
        
        // 记录动态字段的原始值
        dynamicFields.forEach((fieldName, value) -> {
            originalValues.put(fieldName, value);
        });
        
        // 尝试记录父类字段的原始值
        try {
            if (super.hasField("id")) originalValues.put("id", super.getField("id"));
            if (super.hasField("name")) originalValues.put("name", super.getField("name"));
            if (super.hasField("createdDate")) originalValues.put("createdDate", super.getField("createdDate"));
            if (super.hasField("createdBy")) originalValues.put("createdBy", super.getField("createdBy"));
            if (super.hasField("lastModifiedDate")) originalValues.put("lastModifiedDate", super.getField("lastModifiedDate"));
            if (super.hasField("lastModifiedBy")) originalValues.put("lastModifiedBy", super.getField("lastModifiedBy"));
        } catch (Exception ignored) {
            // 忽略不存在的字段异常
        }
        
        this.modified = false;
    }
    
    /**
     * 获取修改的字段
     * @return 修改的字段映射
     */
    public Map<String, Object> getModifiedFields() {
        Map<String, Object> modifiedFields = new HashMap<>();
        
        // 检查动态字段
        dynamicFields.forEach((fieldName, value) -> {
            Object originalValue = originalValues.get(fieldName);
            if (originalValue == null && value != null || originalValue != null && !originalValue.equals(value)) {
                modifiedFields.put(fieldName, value);
            }
        });
        
        // 检查父类字段
        try {
            if (super.hasField("id")) {
                Object value = super.getField("id");
                Object originalValue = originalValues.get("id");
                if (originalValue == null && value != null || originalValue != null && !originalValue.equals(value)) {
                    modifiedFields.put("id", value);
                }
            }
            // 可以添加更多父类字段检查
        } catch (Exception ignored) {
            // 忽略异常
        }
        
        return modifiedFields;
    }
    
    /**
     * 重置为原始状态
     */
    public void reset() {
        // 重置动态字段
        dynamicFields.clear();
        originalValues.forEach((fieldName, value) -> {
            if (value != null) {
                dynamicFields.put(fieldName, value);
            }
        });
        
        // 重置父类字段（如果可能）
        try {
            originalValues.forEach((fieldName, value) -> {
                try {
                    super.setField(fieldName, value);
                } catch (Exception ignored) {
                    // 忽略父类没有的字段
                }
            });
        } catch (Exception ignored) {
            // 忽略异常
        }
        
        this.modified = false;
        clearCaches();
    }

    /**
     * 获取所有字段键值对
     */
    public Map<String, Object> getAllFields() {
        Map<String, Object> allFields = new HashMap<>();
        
        // 添加父类字段（如果可能）
        try {
            if (super.hasField("id")) allFields.put("id", super.getField("id"));
            if (super.hasField("name")) allFields.put("name", super.getField("name"));
            if (super.hasField("createdDate")) allFields.put("createdDate", super.getField("createdDate"));
            if (super.hasField("createdBy")) allFields.put("createdBy", super.getField("createdBy"));
            if (super.hasField("lastModifiedDate")) allFields.put("lastModifiedDate", super.getField("lastModifiedDate"));
            if (super.hasField("lastModifiedBy")) allFields.put("lastModifiedBy", super.getField("lastModifiedBy"));
            if (super.hasField("systemModstamp")) allFields.put("systemModstamp", super.getField("systemModstamp"));
            if (super.hasField("isDeleted")) allFields.put("isDeleted", super.getField("isDeleted"));
        } catch (Exception ignored) {
            // 忽略不存在的字段异常
            // 添加默认值作为备选
            allFields.put("id", null);
            allFields.put("name", null);
            allFields.put("createdDate", null);
            allFields.put("createdBy", null);
            allFields.put("lastModifiedDate", null);
            allFields.put("lastModifiedBy", null);
            allFields.put("systemModstamp", null);
            allFields.put("isDeleted", false);
        }
        
        // 添加动态字段
        allFields.putAll(dynamicFields);
        
        // 添加计算字段和虚拟字段（可选）
        // allFields.putAll(calculatedFieldValues);
        // allFields.putAll(virtualFieldValues);
        
        return allFields;
    }
    
    /**
     * 复制实体
     * @return 实体副本
     */
    public DynamicSmartEntity copy() {
        DynamicSmartEntity copy = new DynamicSmartEntity(entityType, entityApiName);
        
        // 复制动态字段
        if (dynamicFields != null) {
            copy.setDynamicFields(new HashMap<>(dynamicFields));
        }
        
        // 复制基本属性
        copy.setRecordType(recordType);
        copy.setNewEntity(newEntity);
        copy.setDeleted(deleted);
        copy.setModified(modified);
        
        // 尝试复制父类字段
        try {
            Map<String, Object> allFields = getAllFields();
            allFields.forEach((fieldName, value) -> {
                try {
                    copy.setField(fieldName, value);
                } catch (Exception ignored) {
                    // 忽略无法复制的字段
                }
            });
        } catch (Exception ignored) {
            // 忽略异常
        }
        
        return copy;
    }
    
    @Override
    public String toString() {
        return "DynamicSmartEntity{" +
                "entityType='" + entityType + "'" +
                ", entityApiName='" + entityApiName + "'" +
                ", dynamicFields=" + dynamicFields + "}";
    }
}
