package com.bone.metadata.engine.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作条件定义 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationCondition {
  // 移除NotBlank注解，简化实现
  private String expression; // SpEL表达式

  private String errorMessage;

  @Builder.Default private ConditionType type = ConditionType.PRECONDITION;

  public enum ConditionType {
    PRECONDITION, // 前置条件
    POSTCONDITION // 后置条件
  }
}
