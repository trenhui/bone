package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的字段元数据模型类
 * 支持验证规则、计算字段、虚拟字段、关系字段和安全控制等功能
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMetadata {
    // 基础信息
    private String id;
    private String apiName;
    private String label;
    @Builder.Default
    private Map<String, String> labels = new HashMap<>(); // 多语言标签
    private String description;
    private String type;
    private String domain;
    
    // 约束信息
    private boolean required;
    private boolean unique;
    private Integer minLength;
    private Integer maxLength;
    private Double minValue;
    private Double maxValue;
    private String pattern;
    @Builder.Default
    private List<String> picklistValues = new ArrayList<>();
    
    // 计算字段信息
    private boolean calculated;
    private String calculationExpression;
    @Builder.Default
    private List<String> calculationDependencies = new ArrayList<>();
    private boolean virtual;
    
    // 关系信息
    private String referenceTo;
    private RelationshipType relationshipType;
    private String relationshipName;
    private String inverseField;
    private boolean cascadeDelete;
    private boolean orphanRemoval;
    private int batchSize = 10;
    
    // 安全信息
    private String sensitivityLevel;
    private FieldPermission permission;
    private boolean encrypted;
    private String encryptionAlgorithm;
    
    // UI配置
    private String fieldGroup;
    private boolean showInList = true;
    private boolean showInDetail = true;
    private Integer displayOrder;
    private String widgetType;
    @Builder.Default
    private Map<String, Object> uiAttributes = new HashMap<>();
    
    // AI配置
    private boolean aiAutoFillEnabled;
    private String aiPrompt;
    private boolean aiGenerated;
    
    // 数据库配置
    private String columnName;
    private boolean indexed;
    private boolean primaryKey;
    private boolean systemField;
    private int length = 255;
    private int precision;
    private int scale;
    
    // 默认值配置
    private String defaultValue;
    private String defaultExpression;
    
    // 查询配置
    private boolean searchable = true;
    private boolean sortable = true;
    private String searchAnalyzer;
    private String indexAnalyzer;
    
    // 生命周期信息
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // 元数据版本信息
    private String version;
    private String previousVersionId;
    
    // 业务状态
    private boolean active = true;
    
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
     * 字段权限枚举
     */
    public enum FieldPermission {
        READ_ONLY,
        READ_WRITE,
        HIDDEN,
        SYSTEM
    }
    
    /**
     * 检查字段是否为关系字段
     */
    public boolean isRelationshipField() {
        return referenceTo != null && !referenceTo.isEmpty();
    }
    
    /**
     * 检查字段是否可编辑
     */
    public boolean isEditable() {
        return !systemField && !calculated && permission != FieldPermission.READ_ONLY && permission != FieldPermission.HIDDEN;
    }
    
    /**
     * 检查字段是否可见
     */
    public boolean isVisible() {
        return permission != FieldPermission.HIDDEN && permission != FieldPermission.SYSTEM;
    }
    
    /**
     * 获取字段的有效类型
     */
    public String getEffectiveType() {
        // 如果是计算字段且指定了返回类型，则使用指定的类型
        if (calculated && calculationExpression != null) {
            // 可以根据计算表达式推断类型
            return type;
        }
        return type;
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
     * 添加计算依赖字段
     */
    public void addCalculationDependency(String fieldName) {
        if (calculationDependencies == null) {
            calculationDependencies = new ArrayList<>();
        }
        if (!calculationDependencies.contains(fieldName)) {
            calculationDependencies.add(fieldName);
        }
    }
    
    /**
     * 设置UI属性
     */
    public void setUiAttribute(String key, Object value) {
        if (uiAttributes == null) {
            uiAttributes = new HashMap<>();
        }
        uiAttributes.put(key, value);
    }
    
    /**
     * 获取UI属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getUiAttribute(String key, T defaultValue) {
        if (uiAttributes != null && uiAttributes.containsKey(key)) {
            return (T) uiAttributes.get(key);
        }
        return defaultValue;
    }
    
    /**
     * 克隆字段元数据（用于版本管理）
     */
    public FieldMetadata cloneForVersion() {
        FieldMetadata clone = FieldMetadata.builder()
                .apiName(this.apiName)
                .label(this.label)
                .type(this.type)
                .description(this.description)
                .domain(this.domain)
                .required(this.required)
                .unique(this.unique)
                .minLength(this.minLength)
                .maxLength(this.maxLength)
                .minValue(this.minValue)
                .maxValue(this.maxValue)
                .pattern(this.pattern)
                .calculated(this.calculated)
                .calculationExpression(this.calculationExpression)
                .virtual(this.virtual)
                .referenceTo(this.referenceTo)
                .relationshipType(this.relationshipType)
                .sensitivityLevel(this.sensitivityLevel)
                .permission(this.permission)
                .encrypted(this.encrypted)
                .encryptionAlgorithm(this.encryptionAlgorithm)
                .fieldGroup(this.fieldGroup)
                .showInList(this.showInList)
                .showInDetail(this.showInDetail)
                .displayOrder(this.displayOrder)
                .widgetType(this.widgetType)
                .aiAutoFillEnabled(this.aiAutoFillEnabled)
                .aiPrompt(this.aiPrompt)
                .columnName(this.columnName)
                .indexed(this.indexed)
                .primaryKey(this.primaryKey)
                .systemField(this.systemField)
                .length(this.length)
                .precision(this.precision)
                .scale(this.scale)
                .defaultValue(this.defaultValue)
                .defaultExpression(this.defaultExpression)
                .searchable(this.searchable)
                .sortable(this.sortable)
                .searchAnalyzer(this.searchAnalyzer)
                .indexAnalyzer(this.indexAnalyzer)
                .version(null) // 新版本
                .previousVersionId(this.id)
                .active(this.active)
                .build();
        
        // 复制集合和映射
        if (this.labels != null) {
            clone.setLabels(new HashMap<>(this.labels));
        }
        if (this.picklistValues != null) {
            clone.setPicklistValues(new ArrayList<>(this.picklistValues));
        }
        if (this.calculationDependencies != null) {
            clone.setCalculationDependencies(new ArrayList<>(this.calculationDependencies));
        }
        if (this.uiAttributes != null) {
            clone.setUiAttributes(new HashMap<>(this.uiAttributes));
        }
        
        return clone;
    }
}