package com.bone.metadata.engine.expression;

import com.bone.metadata.engine.ExpressionEngine;
import java.util.HashMap;
import java.util.Map;

public class TestEval {
  public static void main(String[] args) {
    System.out.println("Starting TestEval...");

    // 创建ExpressionEngine实例
    ExpressionEngine engine = new ExpressionEngine();

    // 创建上下文
    Map<String, Object> context = new HashMap<>();
    context.put("age", 20);

    // 测试表达式 - 使用#前缀访问变量
    String expression = "#age > 18";
    System.out.println("Testing expression: " + expression);

    try {
      // 执行表达式
      Object result = engine.eval(expression, context);
      System.out.println("Result: " + result);
      System.out.println("Test passed successfully!");
    } catch (Exception e) {
      System.err.println("Test failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
