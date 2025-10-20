package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 统一的实体元数据模型类
 * 支持字段、关系、业务规则、操作、流程等完整的元数据定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMetadata {
    // 基础信息
    private String id;
    private String name;
    private String apiName;
    private String label;
    @Builder.Default
    private Map<String, String> labels = new HashMap<>(); // 多语言标签
    private String description;
    private String domain;
    private String tableName;
    
    // 字段信息
    @Builder.Default
    private Map<String, FieldMetadata> fields = new HashMap<>();
    
    // 关系信息
    @Builder.Default
    private Map<String, RelationshipMetadata> relationships = new HashMap<>();
    
    // 业务规则
    @Builder.Default
    private List<BusinessRuleMetadata> businessRules = new ArrayList<>();
    
    // 操作信息
    @Builder.Default
    private Map<String, OperationMetadata> operations = new HashMap<>();
    
    // 流程信息
    @Builder.Default
    private List<ProcessMetadata> processes = new ArrayList<>();
    
    // 索引信息
    @Builder.Default
    private List<IndexMetadata> indexes = new ArrayList<>();
    
    // 权限信息
    private EntityPermissionMetadata permissions;
    
    // 版本信息
    private String version;
    private String parentEntity;
    
    // 配置信息
    private boolean active = true;
    private boolean system = false;
    private boolean cacheable = true;
    private int queryCacheTtl = 300; // 默认5分钟
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    // AI信息
    private AiMetadata aiMetadata;
    
    // 生命周期信息
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // 租户信息
    private String tenantId;
    
    // 高级配置
    private boolean trackHistory;
    private boolean softDelete;
    private String softDeleteField;
    private boolean optimisticLocking;
    private String versionField;
    
    /**
     * 添加字段元数据
     */
    public void addField(FieldMetadata field) {
        if (fields == null) {
            fields = new HashMap<>();
        }
        fields.put(field.getApiName(), field);
    }
    
    /**
     * 获取字段元数据
     */
    public FieldMetadata getField(String fieldName) {
        return fields != null ? fields.get(fieldName) : null;
    }
    
    /**
     * 移除字段元数据
     */
    public FieldMetadata removeField(String fieldName) {
        return fields != null ? fields.remove(fieldName) : null;
    }
    
    /**
     * 检查字段是否存在
     */
    public boolean hasField(String fieldName) {
        return fields != null && fields.containsKey(fieldName);
    }
    
    /**
     * 获取所有字段名
     */
    public Set<String> getFieldNames() {
        return fields != null ? fields.keySet() : Collections.emptySet();
    }
    
    /**
     * 获取所有字段列表
     */
    public Collection<FieldMetadata> getAllFields() {
        return fields != null ? fields.values() : Collections.emptyList();
    }
    
    /**
     * 获取所有计算字段
     */
    public List<FieldMetadata> getCalculatedFields() {
        List<FieldMetadata> result = new ArrayList<>();
        if (fields != null) {
            for (FieldMetadata field : fields.values()) {
                if (field.isCalculated()) {
                    result.add(field);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取所有虚拟字段
     */
    public List<FieldMetadata> getVirtualFields() {
        List<FieldMetadata> result = new ArrayList<>();
        if (fields != null) {
            for (FieldMetadata field : fields.values()) {
                if (field.isVirtual()) {
                    result.add(field);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取所有关系字段
     */
    public List<FieldMetadata> getRelationshipFields() {
        List<FieldMetadata> result = new ArrayList<>();
        if (fields != null) {
            for (FieldMetadata field : fields.values()) {
                if (field.isRelationshipField()) {
                    result.add(field);
                }
            }
        }
        return result;
    }
    
    /**
     * 添加关系元数据
     */
    public void addRelationship(RelationshipMetadata relationship) {
        if (relationships == null) {
            relationships = new HashMap<>();
        }
        relationships.put(relationship.getApiName(), relationship);
    }
    
    /**
     * 获取关系元数据
     */
    public RelationshipMetadata getRelationship(String relationshipName) {
        return relationships != null ? relationships.get(relationshipName) : null;
    }
    
    /**
     * 移除关系元数据
     */
    public RelationshipMetadata removeRelationship(String relationshipName) {
        return relationships != null ? relationships.remove(relationshipName) : null;
    }
    
    /**
     * 检查关系是否存在
     */
    public boolean hasRelationship(String relationshipName) {
        return relationships != null && relationships.containsKey(relationshipName);
    }
    
    /**
     * 获取所有关系名
     */
    public Set<String> getRelationshipNames() {
        return relationships != null ? relationships.keySet() : Collections.emptySet();
    }
    
    /**
     * 获取所有关系列表
     */
    public Collection<RelationshipMetadata> getAllRelationships() {
        return relationships != null ? relationships.values() : Collections.emptyList();
    }
    
    /**
     * 添加业务规则
     */
    public void addBusinessRule(BusinessRuleMetadata rule) {
        if (businessRules == null) {
            businessRules = new ArrayList<>();
        }
        businessRules.add(rule);
    }
    
    /**
     * 添加操作元数据
     */
    public void addOperation(OperationMetadata operation) {
        if (operations == null) {
            operations = new HashMap<>();
        }
        operations.put(operation.getApiName(), operation);
    }
    
    /**
     * 获取操作元数据
     */
    public OperationMetadata getOperation(String operationName) {
        return operations != null ? operations.get(operationName) : null;
    }
    
    /**
     * 移除操作元数据
     */
    public OperationMetadata removeOperation(String operationName) {
        return operations != null ? operations.remove(operationName) : null;
    }
    
    /**
     * 添加索引元数据
     */
    public void addIndex(IndexMetadata index) {
        if (indexes == null) {
            indexes = new ArrayList<>();
        }
        indexes.add(index);
    }
    
    /**
     * 添加标签
     */
    public void addTag(String tag) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }
    
    /**
     * 检查是否包含标签
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
    
    /**
     * 添加多语言标签
     */
    public void addLabel(String locale, String value) {
        if (labels == null) {
            labels = new HashMap<>();
        }
        labels.put(locale, value);
    }
    
    /**
     * 获取指定语言的标签
     */
    public String getLabel(String locale) {
        if (labels != null && labels.containsKey(locale)) {
            return labels.get(locale);
        }
        return label; // 返回默认标签
    }
    
    /**
     * 获取主键字段
     */
    public FieldMetadata getPrimaryKeyField() {
        if (fields != null) {
            for (FieldMetadata field : fields.values()) {
                if (field.isPrimaryKey()) {
                    return field;
                }
            }
        }
        // 如果没有显式主键，查找ID字段
        return getField("id");
    }
    
    /**
     * 获取主键字段名
     */
    public String getPrimaryKeyFieldName() {
        FieldMetadata pkField = getPrimaryKeyField();
        return pkField != null ? pkField.getApiName() : "id";
    }
    
    /**
     * 检查字段是否为系统字段
     */
    public boolean isSystemField(String fieldName) {
        FieldMetadata field = getField(fieldName);
        return field != null && field.isSystemField();
    }
    
    /**
     * 克隆实体元数据（用于版本管理）
     */
    public EntityMetadata cloneForVersion() {
        EntityMetadata clone = EntityMetadata.builder()
                .name(this.name)
                .apiName(this.apiName)
                .label(this.label)
                .description(this.description)
                .domain(this.domain)
                .tableName(this.tableName)
                .version(null) // 新版本
                .parentEntity(this.apiName)
                .previousVersionId(this.id)
                .active(this.active)
                .system(this.system)
                .cacheable(this.cacheable)
                .queryCacheTtl(this.queryCacheTtl)
                .trackHistory(this.trackHistory)
                .softDelete(this.softDelete)
                .softDeleteField(this.softDeleteField)
                .optimisticLocking(this.optimisticLocking)
                .versionField(this.versionField)
                .tenantId(this.tenantId)
                .build();
        
        // 复制集合和映射
        if (this.labels != null) {
            clone.setLabels(new HashMap<>(this.labels));
        }
        if (this.tags != null) {
            clone.setTags(new ArrayList<>(this.tags));
        }
        
        // 复制字段，创建新版本的字段
        if (this.fields != null) {
            Map<String, FieldMetadata> clonedFields = new HashMap<>();
            for (FieldMetadata field : this.fields.values()) {
                FieldMetadata clonedField = field.cloneForVersion();
                clonedFields.put(field.getApiName(), clonedField);
            }
            clone.setFields(clonedFields);
        }
        
        // 复制关系，创建新版本的关系
        if (this.relationships != null) {
            Map<String, RelationshipMetadata> clonedRelationships = new HashMap<>();
            for (RelationshipMetadata relationship : this.relationships.values()) {
                RelationshipMetadata clonedRelationship = relationship.cloneForVersion();
                clonedRelationships.put(relationship.getApiName(), clonedRelationship);
            }
            clone.setRelationships(clonedRelationships);
        }
        
        // 复制业务规则
        if (this.businessRules != null) {
            clone.setBusinessRules(new ArrayList<>(this.businessRules));
        }
        
        // 复制操作
        if (this.operations != null) {
            clone.setOperations(new HashMap<>(this.operations));
        }
        
        // 复制流程
        if (this.processes != null) {
            clone.setProcesses(new ArrayList<>(this.processes));
        }
        
        // 复制索引
        if (this.indexes != null) {
            clone.setIndexes(new ArrayList<>(this.indexes));
        }
        
        // 复制权限信息
        if (this.permissions != null) {
            // 这里应该复制permissions对象
            clone.setPermissions(this.permissions);
        }
        
        // 复制AI元数据
        if (this.aiMetadata != null) {
            // 这里应该复制aiMetadata对象
            clone.setAiMetadata(this.aiMetadata);
        }
        
        return clone;
    }
}