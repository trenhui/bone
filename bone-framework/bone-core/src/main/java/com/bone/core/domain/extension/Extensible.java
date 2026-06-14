package com.bone.core.domain.extension;

import com.bone.core.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.Serializable;
import java.util.*;

/**
 * 可扩展实体接口（线程安全设计）
 *
 * <p>领域对象通过实现该接口获得动态扩展能力，支持： 1. 动态属性存取（线程安全） 2. 类型安全访问 3. 元数据管理
 */
public interface Extensible extends Serializable {

  /**
   * 获取扩展属性集合（不可变视图）
   *
   * <p>通过返回不可修改的快照保证线程安全
   *
   * @return 扩展属性集合
   */
  Map<String, Object> getExtraProperties();

  String getBizIdentityCode();

  /**
   * 添加扩展属性（线程安全）
   *
   * @param key 属性键，推荐格式：domain.feature.property（如 finance.taxRate）
   * @param value 属性值（需可序列化）
   */
  default void putExtraProperty(String key, Object value) {
    if (key == null || key.trim().isEmpty()) {
      throw new IllegalArgumentException("属性键不能为空");
    }
    getExtraProperties().put(key, value);
  }

  /**
   * 批量合并扩展属性（线程安全）
   *
   * @param properties 要合并的属性集合
   */
  default void mergeExtraProperties(Map<String, ?> properties) {
    if (properties != null && !properties.isEmpty()) {
      getExtraProperties().putAll(properties);
    }
  }

  /**
   * 类型安全获取扩展属性
   *
   * @param key 属性键
   * @param type 目标类型
   * @param <T> 类型参数
   * @return Optional包装的属性值，未找到或类型不匹配时返回空
   */
  default <T> Optional<T> getExtraProperty(String key, Class<T> type) {
    Object value = getExtraProperties().get(key);
    return Optional.ofNullable(value).flatMap(v -> convertValue(v, type));
  }

  private <T> Optional<T> convertValue(Object value, Class<T> type) {
    // 处理枚举类型
    if (type.isEnum()) {
      return convertToEnum(value, type);
    }

    // 尝试JSON反序列化
    Optional<T> jsonResult = tryParseJson(value, type);
    if (jsonResult.isPresent()) {
      return jsonResult;
    }

    // 默认类型转换
    return Optional.of(value).filter(type::isInstance).map(type::cast);
  }

  private <T> Optional<T> tryParseJson(Object value, Class<T> targetType) {
    // 前置条件检查
    if (!(value instanceof String)) return Optional.empty();
    String jsonStr = (String) value;
    if (jsonStr.isEmpty()) return Optional.empty();

    // 智能判断是否需要JSON解析
    if (!shouldParseJson(targetType)) return Optional.empty();

    // 执行反序列化
    try {
      T result = parseWithJsonStr(jsonStr, targetType);
      return Optional.ofNullable(result);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /** 智能反序列化决策逻辑： 1. 非JDK内置类型 2. 集合/数组类型 3. 接口类型需要特殊处理（根据项目需求扩展） */
  private boolean shouldParseJson(Class<?> targetType) {
    return !isJdkClass(targetType) || isJdkCollection(targetType) || targetType.isInterface();
  }

  @SuppressWarnings("unchecked")
  private <T> T parseWithJsonStr(String jsonStr, Class<T> targetType) {
    // 处理集合类型（根据项目需求扩展更多类型）
    if (List.class.isAssignableFrom(targetType)) {
      return (T) JsonUtil.fromJson(jsonStr, new TypeReference<List<?>>() {});
    }
    if (Set.class.isAssignableFrom(targetType)) {
      return (T) JsonUtil.fromJson(jsonStr, new TypeReference<Set<?>>() {});
    }
    if (Map.class.isAssignableFrom(targetType)) {
      return (T) JsonUtil.fromJson(jsonStr, new TypeReference<Map<?, ?>>() {});
    }
    if (targetType.isArray()) {
      return JsonUtil.fromJson(jsonStr, targetType);
    }

    // 处理自定义POJO
    return JsonUtil.fromJson(jsonStr, targetType);
  }

  /* 类判断工具方法 */
  private boolean isJdkClass(Class<?> clazz) {
    return clazz.getName().startsWith("java.") || clazz.getName().startsWith("javax.");
  }

  private boolean isJdkCollection(Class<?> clazz) {
    return Collection.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz)
        || clazz.isArray();
  }

  @SuppressWarnings("unchecked")
  private <T> Optional<T> convertToEnum(Object value, Class<T> enumType) {
    // 如果已经是目标枚举类型，直接返回
    if (enumType.isInstance(value)) {
      return Optional.of((T) value);
    }

    // 尝试字符串转枚举
    if (value instanceof String) {
      String strValue = (String) value;
      try {
        return Optional.of((T) Enum.valueOf((Class<Enum>) enumType, strValue));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }

    // 尝试数字转枚举（按序数）
    if (value instanceof Number) {
      int ordinal = ((Number) value).intValue();
      T[] constants = enumType.getEnumConstants();
      if (ordinal >= 0 && ordinal < constants.length) {
        return Optional.of(constants[ordinal]);
      }
    }

    return Optional.empty();
  }

  /**
   * 类型安全获取扩展属性
   *
   * @param key 属性键
   * @return 未找到或类型不匹配时返回空
   */
  default Object getExtraProperty(String key) {
    return getExtraProperties().get(key);
  }

  /**
   * 获取带默认值的扩展属性
   *
   * @param key 属性键
   * @param type 目标类型
   * @param defaultValue 默认值
   * @param <T> 类型参数
   * @return 属性值或默认值
   */
  default <T> T getExtraProperty(String key, Class<T> type, T defaultValue) {
    return getExtraProperty(key, type).orElse(defaultValue);
  }

  /**
   * 检查是否包含指定扩展属性
   *
   * @param key 属性键
   * @return 是否存在
   */
  default boolean containsExtraProperty(String key) {
    return getExtraProperties().containsKey(key);
  }

  /**
   * 获取所有扩展属性键集合
   *
   * @return 不可修改的键集合
   */
  default Set<String> getExtraPropertyKeys() {
    return Collections.unmodifiableSet(getExtraProperties().keySet());
  }
}
