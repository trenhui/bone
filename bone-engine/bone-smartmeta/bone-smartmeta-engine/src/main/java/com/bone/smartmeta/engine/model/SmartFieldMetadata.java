package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能字段元数据类
 * 继承自基础字段元数据，提供更高级的字段特性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartFieldMetadata extends FieldMetadata {
    // 字段计算表达式
    private String calculationExpression;
    
    // 默认值表达式
    private String defaultValueExpression;
    
    // 字段格式化模板
    private String formatPattern;
    
    // 字段验证表达式
    private String validationExpression;
    
    // 是否为虚拟字段（不存储在数据库中）
    private boolean virtual = false;
    
    // 是否为计算字段
    private boolean calculated = false;
    
    // 是否为只读字段
    private boolean readonly = false;
    
    // 字段数据字典
    private String dataDictionaryCode;
    
    // 字段关联的业务规则
    private String businessRuleCode;
    
    // 字段的条件显示规则
    private String conditionalDisplayRule;
    
    @Override
    public String getCalculationExpression() {
        return this.calculationExpression;
    }
    
    /**
     * 判断字段是否为虚拟字段
     */
    public boolean isVirtual() {
        return this.virtual;
    }
    
    /**
     * 判断字段是否为计算字段
     */
    public boolean isCalculated() {
        return this.calculated;
    }
    
    /**
     * 获取字段的显示值
     */
    public Object getDisplayValue(Object rawValue) {
        // 简单实现，实际可能需要更复杂的格式化逻辑
        if (formatPattern != null && rawValue != null) {
            // 这里可以实现格式化逻辑
            return String.format(formatPattern, rawValue);
        }
        return rawValue;
    }
}