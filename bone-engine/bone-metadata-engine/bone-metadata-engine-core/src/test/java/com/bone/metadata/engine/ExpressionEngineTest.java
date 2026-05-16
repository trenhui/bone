package com.bone.metadata.engine;

import java.util.HashMap;
import java.util.Map;

public class ExpressionEngineTest {

  public static void main(String[] args) {
    System.out.println("Starting ExpressionEngineTest...");

    // 创建ExpressionEngine实例
    ExpressionEngine engine = new ExpressionEngine();

    // 创建上下文
    Map<String, Object> context = new HashMap<>();
    context.put("age", 20);
    context.put("name", "Test");

    try {
      // 测试表达式 - 使用#前缀访问变量
      Object result1 = engine.eval("#age > 18", context);
      System.out.println("Test 1: #age > 18 = " + result1);

      Object result2 = engine.eval("#name", context);
      System.out.println("Test 2: #name = " + result2);

      Object result3 = engine.eval("#age + 5", context);
      System.out.println("Test 3: #age + 5 = " + result3);

      System.out.println("All tests completed!");
    } catch (Exception e) {
      System.err.println("Test failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
