package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体关系元数据模型类
 * 用于定义实体之间的关联关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipMetadata {
    // 基础信息
    private String id;
    private String name;
    private String apiName;
    private String label;
    @Builder.Default
    private Map<String, String> labels = new HashMap<>(); // 多语言标签
    private String description;
    private String domain;
    
    // 关系定义
    private RelationshipType type;
    private String sourceEntity;
    private String sourceField;
    private String targetEntity;
    private String targetField;
    private String inverseRelationshipName;
    
    // 级联操作配置
    private CascadeType cascade;
    private boolean orphanRemoval;
    private int batchSize = 10;
    
    // 加载策略
    private FetchType fetchType = FetchType.LAZY;
    private boolean optional = true;
    
    // 映射配置
    private String joinTable;
    private String joinColumns;
    private String inverseJoinColumns;
    
    // 查询配置
    private String orderBy;
    private int maxResults;
    private boolean readOnly;
    
    // 安全配置
    private String sensitivityLevel;
    private RelationshipPermission permission;
    
    // 业务状态
    private boolean active = true;
    private boolean system = false;
    
    // 生命周期信息
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // 元数据版本信息
    private String version;
    private String previousVersionId;
    
    /**
     * 关系类型枚举
     */
    public enum RelationshipType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }
    
    /**
     * 级联操作类型枚举
     */
    public enum CascadeType {
        ALL,
        PERSIST,
        MERGE,
        REMOVE,
        REFRESH,
        DETACH,
        NONE
    }
    
    /**
     * 加载策略枚举
     */
    public enum FetchType {
        EAGER,
        LAZY
    }
    
    /**
     * 关系权限枚举
     */
    public enum RelationshipPermission {
        READ_ONLY,
        READ_WRITE,
        HIDDEN,
        SYSTEM
    }
    
    /**
     * 获取关系的目标实体名称
     */
    public String getTargetEntityName() {
        return targetEntity;
    }
    
    /**
     * 获取关系的源实体名称
     */
    public String getSourceEntityName() {
        return sourceEntity;
    }
    
    /**
     * 检查是否是双向关系
     */
    public boolean isBidirectional() {
        return inverseRelationshipName != null && !inverseRelationshipName.isEmpty();
    }
    
    /**
     * 检查是否是集合关系（一对多、多对多）
     */
    public boolean isCollectionRelationship() {
        return type == RelationshipType.ONE_TO_MANY || type == RelationshipType.MANY_TO_MANY;
    }
    
    /**
     * 检查是否是单值关系（一对一、多对一）
     */
    public boolean isSingleValueRelationship() {
        return type == RelationshipType.ONE_TO_ONE || type == RelationshipType.MANY_TO_ONE;
    }
    
    /**
     * 检查是否需要级联删除
     */
    public boolean shouldCascadeDelete() {
        return cascade == CascadeType.ALL || cascade == CascadeType.REMOVE;
    }
    
    /**
     * 检查是否需要级联保存
     */
    public boolean shouldCascadeSave() {
        return cascade == CascadeType.ALL || cascade == CascadeType.PERSIST;
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
     * 获取关系的反向类型
     */
    public RelationshipType getInverseType() {
        switch (type) {
            case ONE_TO_ONE:
                return RelationshipType.ONE_TO_ONE;
            case ONE_TO_MANY:
                return RelationshipType.MANY_TO_ONE;
            case MANY_TO_ONE:
                return RelationshipType.ONE_TO_MANY;
            case MANY_TO_MANY:
                return RelationshipType.MANY_TO_MANY;
            default:
                return type;
        }
    }
    
    /**
     * 克隆关系元数据（用于版本管理）
     */
    public RelationshipMetadata cloneForVersion() {
        RelationshipMetadata clone = RelationshipMetadata.builder()
                .name(this.name)
                .apiName(this.apiName)
                .label(this.label)
                .description(this.description)
                .domain(this.domain)
                .type(this.type)
                .sourceEntity(this.sourceEntity)
                .sourceField(this.sourceField)
                .targetEntity(this.targetEntity)
                .targetField(this.targetField)
                .inverseRelationshipName(this.inverseRelationshipName)
                .cascade(this.cascade)
                .orphanRemoval(this.orphanRemoval)
                .batchSize(this.batchSize)
                .fetchType(this.fetchType)
                .optional(this.optional)
                .joinTable(this.joinTable)
                .joinColumns(this.joinColumns)
                .inverseJoinColumns(this.inverseJoinColumns)
                .orderBy(this.orderBy)
                .maxResults(this.maxResults)
                .readOnly(this.readOnly)
                .sensitivityLevel(this.sensitivityLevel)
                .permission(this.permission)
                .active(this.active)
                .system(this.system)
                .version(null) // 新版本
                .previousVersionId(this.id)
                .build();
        
        // 复制映射
        if (this.labels != null) {
            clone.setLabels(new HashMap<>(this.labels));
        }
        
        return clone;
    }
}