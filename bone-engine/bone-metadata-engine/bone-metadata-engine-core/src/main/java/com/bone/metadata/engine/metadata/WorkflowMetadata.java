package com.bone.metadata.engine.metadata;

import java.util.ArrayList;
import java.util.List;

/** 工作流元数据模型类 */
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

  // Getters and Setters
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getTargetEntity() {
    return targetEntity;
  }

  public void setTargetEntity(String targetEntity) {
    this.targetEntity = targetEntity;
  }

  public String getInitialState() {
    return initialState;
  }

  public void setInitialState(String initialState) {
    this.initialState = initialState;
  }

  public List<WorkflowState> getStates() {
    return states;
  }

  public void setStates(List<WorkflowState> states) {
    this.states = states;
  }

  public List<WorkflowTransition> getTransitions() {
    return transitions;
  }

  public void setTransitions(List<WorkflowTransition> transitions) {
    this.transitions = transitions;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
