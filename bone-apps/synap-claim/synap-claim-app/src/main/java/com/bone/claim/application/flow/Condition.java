package com.bone.claim.application.flow;

/**
 * 声明式条件：只存表达式文本，不在流程层解析。
 *
 * <p>求值一律交给 {@code ScriptConditionPort} 的受控运行时；可用操作符与内置函数由端口上的 {@code ALLOWED_OPERATOR_CHARS} /
 * {@code ALLOWED_OPERATORS} 白名单决定，流程层不另设一套语法。
 */
public record Condition(String expression) {

  public Condition {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("条件表达式不能为空");
    }
  }
}
