package com.bone.metadata.engine.rule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 自定义函数注册表 负责管理和存储可在规则表达式中使用的自定义函数 */
@Component
public class CustomFunctionRegistry {

  private static final Logger log = LoggerFactory.getLogger(CustomFunctionRegistry.class);

  // 存储自定义函数的映射
  private final Map<String, FunctionDefinition> functionRegistry = new ConcurrentHashMap<>();

  /**
   * 注册自定义函数
   *
   * @param name 函数名称
   * @param function 函数实现
   * @param description 函数描述
   */
  public void registerFunction(String name, Object function, String description) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("函数名称不能为空");
    }
    if (function == null) {
      throw new IllegalArgumentException("函数实现不能为空");
    }

    FunctionDefinition definition = new FunctionDefinition(name, function, description);
    functionRegistry.put(name, definition);
    log.debug("已注册自定义函数: {}", name);
  }

  /** 注册自定义函数（无描述） */
  public void registerFunction(String name, Object function) {
    registerFunction(name, function, "");
  }

  /**
   * 注销自定义函数
   *
   * @param name 函数名称
   * @return 是否成功注销
   */
  public boolean unregisterFunction(String name) {
    if (functionRegistry.containsKey(name)) {
      functionRegistry.remove(name);
      log.debug("已注销自定义函数: {}", name);
      return true;
    }
    return false;
  }

  /**
   * 获取自定义函数
   *
   * @param name 函数名称
   * @return 函数实现，不存在返回null
   */
  public Object getFunction(String name) {
    FunctionDefinition definition = functionRegistry.get(name);
    return definition != null ? definition.getFunction() : null;
  }

  /** 获取函数定义 */
  public FunctionDefinition getFunctionDefinition(String name) {
    return functionRegistry.get(name);
  }

  /** 检查函数是否已注册 */
  public boolean hasFunction(String name) {
    return functionRegistry.containsKey(name);
  }

  /** 获取所有注册的函数 */
  public Map<String, FunctionDefinition> getAllFunctions() {
    return new ConcurrentHashMap<>(functionRegistry);
  }

  /** 清空所有函数 */
  public void clear() {
    functionRegistry.clear();
    log.debug("已清空所有自定义函数");
  }

  /** 函数定义类 */
  public static class FunctionDefinition {
    private final String name;
    private final Object function;
    private final String description;

    public FunctionDefinition(String name, Object function, String description) {
      this.name = name;
      this.function = function;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public Object getFunction() {
      return function;
    }

    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return "FunctionDefinition{"
          + "name='"
          + name
          + '\''
          + ", description='"
          + description
          + '\''
          + ", functionType="
          + (function != null ? function.getClass().getName() : "null")
          + '}';
    }
  }
}
