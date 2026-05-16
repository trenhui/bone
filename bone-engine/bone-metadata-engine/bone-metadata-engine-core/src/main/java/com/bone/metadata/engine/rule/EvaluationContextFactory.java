package com.bone.metadata.engine.rule;

import com.bone.metadata.engine.metadata.EntityMetadata;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 评估上下文工厂 负责为规则评估创建上下文环境，包含实体数据、元数据和辅助函数 */
@Component
public class EvaluationContextFactory {

  private static final Logger log = LoggerFactory.getLogger(EvaluationContextFactory.class);

  // 上下文缓存
  private final Map<String, EvaluationContext> contextCache = new ConcurrentHashMap<>();

  /**
   * 创建评估上下文
   *
   * @param entityData 实体数据
   * @param metadata 实体元数据
   * @return 评估上下文
   */
  public EvaluationContext createContext(Map<String, Object> entityData, EntityMetadata metadata) {
    // 创建上下文实例
    EvaluationContext context = new EvaluationContext(entityData, metadata);

    // 注册内置函数
    registerBuiltInFunctions(context);

    return context;
  }

  /** 注册内置函数到上下文 */
  private void registerBuiltInFunctions(EvaluationContext context) {
    // 注册hasField函数
    context.registerFunction(
        "hasField",
        args -> {
          if (args.length >= 1 && args[0] instanceof String) {
            String fieldName = (String) args[0];
            return context.getEntityData().containsKey(fieldName);
          }
          return false;
        });

    // 注册isEmpty函数
    context.registerFunction(
        "isEmpty",
        args -> {
          if (args.length < 1) return true;
          Object value = args[0];
          if (value == null) {
            return true;
          }
          if (value instanceof String) {
            return ((String) value).trim().isEmpty();
          }
          if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
          }
          if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
          }
          return false;
        });

    // 注册contains函数
    context.registerFunction(
        "contains",
        args -> {
          if (args.length >= 2 && args[0] instanceof Collection) {
            Collection<?> collection = (Collection<?>) args[0];
            Object element = args[1];
            return collection != null && collection.contains(element);
          }
          return false;
        });

    // 注册size函数
    context.registerFunction(
        "size",
        args -> {
          if (args.length < 1) return 0;
          Object collection = args[0];
          if (collection == null) {
            return 0;
          }
          if (collection instanceof Collection) {
            return ((Collection<?>) collection).size();
          }
          if (collection instanceof Map) {
            return ((Map<?, ?>) collection).size();
          }
          if (collection instanceof String) {
            return ((String) collection).length();
          }
          return 0;
        });
  }

  /** 评估上下文 封装实体数据和元数据，提供函数注册和查找功能 */
  public static class EvaluationContext {
    private final Map<String, Object> entityData;
    private final EntityMetadata metadata;
    private final Map<String, Function<Object[], Object>> functions = new HashMap<>();

    public EvaluationContext(Map<String, Object> entityData, EntityMetadata metadata) {
      this.entityData = entityData != null ? entityData : Collections.emptyMap();
      this.metadata = metadata;
    }

    /** 获取实体数据 */
    public Map<String, Object> getEntityData() {
      return entityData;
    }

    /** 获取实体元数据 */
    public EntityMetadata getMetadata() {
      return metadata;
    }

    /** 注册函数 */
    public void registerFunction(String name, Function<Object[], Object> function) {
      functions.put(name, function);
    }

    /** 获取函数 */
    public Function<Object[], Object> getFunction(String name) {
      return functions.get(name);
    }

    /** 检查是否包含函数 */
    public boolean hasFunction(String name) {
      return functions.containsKey(name);
    }

    /** 获取属性值 */
    public Object getProperty(String name) {
      if (entityData.containsKey(name)) {
        return entityData.get(name);
      }
      return null;
    }
  }
}
