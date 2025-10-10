package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流元数据模型类
 */
@Getter
@Setter
public class WorkflowMetadata {
    
    // 工作流名称
    private String name;
    
    // 工作流标签
    private String label;
    
    // 工作流描述
    private String description;
    
    // 关联实体
    private String targetEntity;
    
    // 初始状态
    private String initialState;
    
    // 状态列表
    private List<WorkflowState> states = new ArrayList<>();
    
    // 转换规则列表
    private List<WorkflowTransition> transitions = new ArrayList<>();
    
    // 是否启用
    private boolean enabled = true;
}