package com.bone.engine.extension.api.spi;

import java.util.function.Predicate;

public interface ExpressionEvaluator {

  /**
   * 评估表达式在当前业务上下文中的结果
   *
   * @param expression 表达式字符串
   * @param context 业务上下文
   * @return 表达式求值结果
   * @throws IllegalArgumentException 如果参数无效
   */
  boolean evaluate(String expression, Object context);

  /**
   * 编译表达式为可重用的谓词
   *
   * @param expression 表达式字符串
   * @return 编译后的谓词
   * @throws IllegalArgumentException 如果表达式无效
   */
  Predicate<Object> compile(String expression);
}
