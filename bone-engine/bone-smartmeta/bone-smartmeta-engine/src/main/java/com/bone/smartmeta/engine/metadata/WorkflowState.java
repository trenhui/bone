package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

/**
 * 工作流状态模型类
 */
@Getter
@Setter
public class WorkflowState {
    
    // 状态名称
    private String name;
    
    // 状态标签
    private String label;
    
    // 状态描述
    private String description;
    
    // 是否为最终状态
    private boolean isFinalState = false;
    
    // 状态颜色
    private String color;
}