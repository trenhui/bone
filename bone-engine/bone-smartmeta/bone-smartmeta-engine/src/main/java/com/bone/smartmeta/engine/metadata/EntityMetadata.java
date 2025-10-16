package com.bone.smartmeta.engine.metadata;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

/**
 * 实体元数据
 */
@Data
@NoArgsConstructor
public class EntityMetadata {
    private String id;
    private String name;
    private String apiName;
    private String tableName;
    private String description;
    private String label;
    private String domain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> tags = new ArrayList<>();
    private AiMetadata aiMetadata;
    private Map<String, FieldMetadata> fields = new HashMap<>();
    private boolean active = true;
    private boolean system = false;
    private boolean cacheable = true;
    private int queryCacheTtl = 300; // 300秒，即5分钟
    private Class<?> entityClass;
    private String primaryFieldName;
    private Map<String, String> attributes = new HashMap<>();
    private Map<String, RelationshipMetadata> relationships = new HashMap<>();
    private ValidationRules validationRules = new ValidationRules();
    
    // 显式添加setter方法，确保可以被调用
    public void setId(String id) {
        this.id = id;
    }
    
    public void setApiName(String apiName) {
        this.apiName = apiName;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    public void setAiMetadata(AiMetadata aiMetadata) {
        this.aiMetadata = aiMetadata;
    }
    
    public void setFields(List<FieldMetadata> fieldsList) {
        // 将List转换为Map
        this.fields.clear();
        if (fieldsList != null) {
            for (FieldMetadata field : fieldsList) {
                this.fields.put(field.getApiName(), field);
            }
        }
    }
    
    public void setValidationRules(List<ValidationRuleMetadata> rulesList) {
        this.validationRules.getRules().clear();
        if (rulesList != null) {
            for (ValidationRuleMetadata rule : rulesList) {
                this.validationRules.getRules().put(rule.getName(), rule);
            }
        }
    }

    /**
     * 获取查询缓存TTL（秒）
     */
    public int getQueryCacheTtl() {
        return queryCacheTtl;
    }
    
    /**
     * 获取实体类
     */
    public Class<?> getEntityClass() {
        return entityClass;
    }
    
    /**
     * 获取所有字段
     */
    public Map<String, FieldMetadata> getFields() {
        return fields;
    }
    
    /**
     * 获取验证规则列表
     */
    public List<ValidationRuleMetadata> getValidationRules() {
        return new ArrayList<>(validationRules.getRules().values());
    }
    
    /**
     * 获取API名称
     */
    public String getApiName() {
        return apiName;
    }
    
    /**
     * 检查是否可缓存
     */
    public boolean isCacheable() {
        return cacheable;
    }
    
    /**
     * 验证规则内部类
     */
    @Data
    @NoArgsConstructor
    public static class ValidationRules {
        private boolean validateOnSave = true;
        private boolean validateOnUpdate = true;
        private Map<String, ValidationRuleMetadata> rules = new HashMap<>();
    }
    
    /**
     * 获取表名
     */
    public String getTableName() {
        return tableName;
    }
}
