package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务规则元数据模型 - 定义实体的业务规则
 * 支持验证规则、计算规则、自动化规则、审批规则等
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessRuleMetadata {

    // ================ 核心属性 ================
    
    /**
     * 规则名称
     */
    private String name;
    
    /**
     * 规则描述
     */
    private String description;
    
    /**
     * 规则条件表达式
     */
    private String condition;
    
    /**
     * 规则执行表达式
     */
    private String expression;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 触发事件类型
     */
    private List<String> triggerEvents;
    
    /**
     * 规则优先级
     */
    private Integer priority;
    
    /**
     * 规则严重性
     */
    private String severity;
    
    /**
     * AI风险级别
     */
    private String aiRiskLevel;
    
    /**
     * 是否激活
     */
    private Boolean active;
    
    /**
     * 规则类型
     */
    private String ruleType;
    
    /**
     * 关联字段列表
     */
    private List<String> fieldNames;
    
    // ================ 构造方法与辅助方法 ================
    
    public BusinessRuleMetadata() {
        this.triggerEvents = new ArrayList<>();
        this.priority = 50;
        this.severity = "ERROR";
        this.aiRiskLevel = "LOW";
        this.active = Boolean.TRUE;
        this.ruleType = "VALIDATION";
        this.fieldNames = new ArrayList<>();
    }
    
    /**
     * 添加触发事件
     */
    public BusinessRuleMetadata addTriggerEvent(String event) {
        if (this.triggerEvents == null) {
            this.triggerEvents = new ArrayList<>();
        }
        this.triggerEvents.add(event);
        return this;
    }
    
    /**
     * 添加关联字段
     */
    public BusinessRuleMetadata addFieldName(String fieldName) {
        if (this.fieldNames == null) {
            this.fieldNames = new ArrayList<>();
        }
        this.fieldNames.add(fieldName);
        return this;
    }
    
    /**
     * 检查是否为验证规则
     */
    public boolean isValidationRule() {
        return "VALIDATION".equals(this.ruleType);
    }
    
    /**
     * 检查是否为计算规则
     */
    public boolean isCalculationRule() {
        return "CALCULATION".equals(this.ruleType);
    }
    
    /**
     * 检查是否为自动化规则
     */
    public boolean isAutomationRule() {
        return "AUTOMATION".equals(this.ruleType);
    }
    
    /**
     * 检查是否为审批规则
     */
    public boolean isApprovalRule() {
        return "APPROVAL".equals(this.ruleType);
    }
    
    /**
     * 检查是否为操作规则
     */
    public boolean isActionRule() {
        return "ACTION".equals(this.ruleType);
    }
    
    /**
     * 检查规则是否在指定事件触发
     */
    public boolean isTriggeredBy(String event) {
        return this.triggerEvents != null && 
               (this.triggerEvents.contains(event) || this.triggerEvents.contains("ALL"));
    }
    
    /**
     * 检查是否为错误级别规则
     */
    public boolean isErrorSeverity() {
        return "ERROR".equals(this.severity);
    }
    
    /**
     * 检查是否为警告级别规则
     */
    public boolean isWarningSeverity() {
        return "WARNING".equals(this.severity);
    }
    
    /**
     * 检查是否为信息级别规则
     */
    public boolean isInfoSeverity() {
        return "INFO".equals(this.severity);
    }
    
    /**
     * 检查是否为高风险规则
     */
    public boolean isHighRisk() {
        return "HIGH".equals(this.aiRiskLevel);
    }
    
    /**
     * 检查是否需要条件判断
     */
    public boolean hasCondition() {
        return this.condition != null && !this.condition.trim().isEmpty();
    }
}