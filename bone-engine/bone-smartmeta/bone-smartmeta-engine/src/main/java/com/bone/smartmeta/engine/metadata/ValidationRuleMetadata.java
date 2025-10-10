package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证规则元数据模型类
 */
@Getter
@Setter
public class ValidationRuleMetadata {
    
    // 规则名称
    private String name;
    
    // 规则标签
    private String label;
    
    // 规则描述
    private String description;
    
    // 验证条件表达式
    private String conditionExpression;
    
    // 错误消息
    private String errorMessage;
    
    // 错误位置（字段API名称）
    private String errorLocation;
    
    // 相关字段
    private List<String> relatedFields = new ArrayList<>();
    
    // 是否启用
    private boolean enabled = true;
    
    /**
     * 获取表达式（作为getConditionExpression的别名）
     */
    public String getExpression() {
        return this.conditionExpression;
    }
    
    /**
     * 设置表达式（作为setConditionExpression的别名）
     */
    public void setExpression(String expression) {
        this.conditionExpression = expression;
    }
}