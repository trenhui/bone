package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 重计算元数据模型类
 */
@Getter
@Setter
public class RecalculationMetadata {
    
    // 计算表达式
    private String calculationExpression;
    
    // 触发条件
    private String triggerCondition;
    
    // 依赖字段列表
    private List<String> dependentFields = new ArrayList<>();
    
    // 计算频率
    private String recalculationFrequency; // REAL_TIME, BATCH, ON_SAVE
    
    // 是否启用
    private boolean enabled = true;
}