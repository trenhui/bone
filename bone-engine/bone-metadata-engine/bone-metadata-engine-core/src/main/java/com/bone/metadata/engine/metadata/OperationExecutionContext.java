package com.bone.metadata.engine.metadata;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/** 操作执行上下文 */
@Data
public class OperationExecutionContext {
  private OperationMetadata operation;
  private String entityId;
  private Map<String, Object> parameters;
  private Map<String, Object> context;
  private Map<String, Object> variables = new HashMap<>();
  private Map<String, Object> targetEntity;
  private Object result;
  private String operator;
  private long startTime;
  private long endTime;

  /** 获取变量（支持嵌套访问） */
  @SuppressWarnings("unchecked")
  public <T> T getVariable(String key) {
    if (key.contains(".")) {
      String[] parts = key.split("\\.", 2);
      Object parent = variables.get(parts[0]);
      if (parent instanceof Map) {
        return (T) ((Map<String, Object>) parent).get(parts[1]);
      }
      return null;
    }
    return (T) variables.get(key);
  }

  /** 获取所有变量 */
  public Map<String, Object> getVariables() {
    return new HashMap<>(variables); // 返回副本以防止外部修改
  }

  /** 设置变量 */
  public void setVariable(String key, Object value) {
    variables.put(key, value);
  }

  /** 计算执行时间 */
  public long getExecutionTimeMillis() {
    if (endTime > 0) {
      return endTime - startTime;
    }
    return System.currentTimeMillis() - startTime;
  }

  /** 完成执行 */
  public void complete(Object result) {
    this.result = result;
    this.endTime = System.currentTimeMillis();
  }
}
