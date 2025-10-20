package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务规则元数据模型类
 * 用于定义实体的业务验证规则和条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessRuleMetadata {
    // 基础信息
    private String id;
    private String name;
    private String apiName;
    private String description;
    private String domain;
    
    // 规则定义
    private String condition;
    private String expression;
    private String errorMessage;
    private Severity severity = Severity.ERROR;
    private int priority = 0;
    
    // 触发事件
    @Builder.Default
    private List<String> triggerEvents = new ArrayList<>();
    
    // 规则配置
    private boolean active = true;
    private boolean system = false;
    private boolean stopOnFailure = true;
    private String executionPhase;
    
    // 依赖信息
    @Builder.Default
    private Set<String> dependentFields = new HashSet<>();
    
    // 扩展属性
    private String ruleType;
    private String implementationClass;
    private String scriptLanguage;
    private String scriptContent;
    
    // 生命周期信息
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // 元数据版本信息
    private String version;
    private String previousVersionId;
    
    /**
     * 规则严重性枚举
     */
    public enum Severity {
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }
    
    /**
     * 触发事件常量
     */
    public static final String BEFORE_CREATE = "beforeCreate";
    public static final String AFTER_CREATE = "afterCreate";
    public static final String BEFORE_UPDATE = "beforeUpdate";
    public static final String AFTER_UPDATE = "afterUpdate";
    public static final String BEFORE_DELETE = "beforeDelete";
    public static final String AFTER_DELETE = "afterDelete";
    public static final String BEFORE_VALIDATE = "beforeValidate";
    public static final String AFTER_VALIDATE = "afterValidate";
    
    /**
     * 执行阶段常量
     */
    public static final String PRE_VALIDATION = "preValidation";
    public static final String VALIDATION = "validation";
    public static final String POST_VALIDATION = "postValidation";
    public static final String PRE_PERSIST = "prePersist";
    public static final String POST_PERSIST = "postPersist";
    
    /**
     * 添加触发事件
     */
    public void addTriggerEvent(String event) {
        if (triggerEvents == null) {
            triggerEvents = new ArrayList<>();
        }
        if (!triggerEvents.contains(event)) {
            triggerEvents.add(event);
        }
    }
    
    /**
     * 检查是否包含触发事件
     */
    public boolean hasTriggerEvent(String event) {
        return triggerEvents != null && triggerEvents.contains(event);
    }
    
    /**
     * 添加依赖字段
     */
    public void addDependentField(String fieldName) {
        if (dependentFields == null) {
            dependentFields = new HashSet<>();
        }
        dependentFields.add(fieldName);
    }
    
    /**
     * 检查是否依赖指定字段
     */
    public boolean dependsOnField(String fieldName) {
        return dependentFields != null && dependentFields.contains(fieldName);
    }
    
    /**
     * 检查规则是否在指定事件触发
     */
    public boolean shouldTriggerOn(String event) {
        return hasTriggerEvent(event);
    }
    
    /**
     * 检查规则是否是验证规则
     */
    public boolean isValidationRule() {
        return hasTriggerEvent(BEFORE_VALIDATE) || hasTriggerEvent(AFTER_VALIDATE) || 
               executionPhase == null || executionPhase.equals(VALIDATION);
    }
    
    /**
     * 检查规则是否是前置规则
     */
    public boolean isPreRule() {
        return hasTriggerEvent(BEFORE_CREATE) || hasTriggerEvent(BEFORE_UPDATE) || 
               hasTriggerEvent(BEFORE_DELETE) || hasTriggerEvent(BEFORE_VALIDATE) || 
               PRE_VALIDATION.equals(executionPhase) || PRE_PERSIST.equals(executionPhase);
    }
    
    /**
     * 检查规则是否是后置规则
     */
    public boolean isPostRule() {
        return hasTriggerEvent(AFTER_CREATE) || hasTriggerEvent(AFTER_UPDATE) || 
               hasTriggerEvent(AFTER_DELETE) || hasTriggerEvent(AFTER_VALIDATE) || 
               POST_VALIDATION.equals(executionPhase) || POST_PERSIST.equals(executionPhase);
    }
    
    /**
     * 获取规则的评估顺序键
     */
    public String getEvaluationOrderKey() {
        // 构建评估顺序键：优先级 + 严重性 + ID
        return String.format("%05d:%s:%s", priority, severity, id);
    }
    
    /**
     * 克隆业务规则元数据（用于版本管理）
     */
    public BusinessRuleMetadata cloneForVersion() {
        BusinessRuleMetadata clone = BusinessRuleMetadata.builder()
                .name(this.name)
                .apiName(this.apiName)
                .description(this.description)
                .domain(this.domain)
                .condition(this.condition)
                .expression(this.expression)
                .errorMessage(this.errorMessage)
                .severity(this.severity)
                .priority(this.priority)
                .active(this.active)
                .system(this.system)
                .stopOnFailure(this.stopOnFailure)
                .executionPhase(this.executionPhase)
                .ruleType(this.ruleType)
                .implementationClass(this.implementationClass)
                .scriptLanguage(this.scriptLanguage)
                .scriptContent(this.scriptContent)
                .version(null) // 新版本
                .previousVersionId(this.id)
                .build();
        
        // 复制集合
        if (this.triggerEvents != null) {
            clone.setTriggerEvents(new ArrayList<>(this.triggerEvents));
        }
        if (this.dependentFields != null) {
            clone.setDependentFields(new HashSet<>(this.dependentFields));
        }
        
        return clone;
    }
}