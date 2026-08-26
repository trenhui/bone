package com.bone.metadata.engine.domain.metadata;

import lombok.Getter;
import lombok.Setter;

/** 工作流转换模型类 */
@Getter
@Setter
public class WorkflowTransition {

  // 转换名称
  private String name;

  // 转换标签
  private String label;

  // 源状态
  private String fromState;

  // 目标状态
  private String toState;

  // 执行条件
  private String condition;

  // 执行角色
  private String requiredRole;

  // 描述
  private String description;
}
