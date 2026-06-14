package com.bone.metadata.sdk.support.util;

import com.bone.metadata.sdk.domain.enums.DataType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 类型检测器，用于检测对象的实际数据类型并映射到相应的DataType枚举。 支持自定义类型映射的注册和缓存机制，提高性能和扩展性。 */
public final class TypeDetector {
  // 内置类型映射，包含常用的Java类型到DataType的映射
  private static final Map<Class<?>, DataType> TYPE_MAPPINGS = createTypeMappings();
  // 自定义类型映射，允许外部注册额外的类型映射
  private static final Map<Class<?>, DataType> CUSTOM_TYPE_MAPPINGS = new ConcurrentHashMap<>();
  // 类型检测结果缓存，提高重复检测的性能
  private static final Map<Class<?>, DataType> TYPE_DETECTION_CACHE = new ConcurrentHashMap<>();

  /** 私有构造函数，防止实例化 */
  private TypeDetector() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * 检测对象的实际数据类型
   *
   * @param value 要检测的对象
   * @param textThreshold 文本长度阈值，超过此阈值的字符串会被识别为TEXT类型
   * @return 对应的DataType枚举值
   */
  public static DataType detect(Object value, int textThreshold) {
    if (value == null) {
      return DataType.STRING;
    }

    // 对于字符串类型，先检查长度是否超过阈值
    if (value instanceof String str) {
      return str.length() > textThreshold ? DataType.TEXT : DataType.STRING;
    }

    // 获取对象的实际类型
    Class<?> valueClass = value.getClass();

    // 优先从缓存获取检测结果
    DataType cachedType = TYPE_DETECTION_CACHE.get(valueClass);
    if (cachedType != null) {
      return cachedType;
    }

    // 检查自定义类型映射
    DataType customType = CUSTOM_TYPE_MAPPINGS.get(valueClass);
    if (customType != null) {
      TYPE_DETECTION_CACHE.put(valueClass, customType);
      return customType;
    }

    // 检查精确类型匹配
    DataType exactType = TYPE_MAPPINGS.get(valueClass);
    if (exactType != null) {
      TYPE_DETECTION_CACHE.put(valueClass, exactType);
      return exactType;
    }

    // 检查父类或接口匹配
    DataType type =
        TYPE_MAPPINGS.entrySet().stream()
            .filter(entry -> entry.getKey().isInstance(value))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(DataType.STRING);

    // 缓存检测结果
    TYPE_DETECTION_CACHE.put(valueClass, type);
    return type;
  }

  /**
   * 注册自定义类型映射
   *
   * @param clazz Java类型
   * @param dataType 对应的DataType枚举值
   * @throws IllegalArgumentException 如果参数为null
   */
  public static void registerCustomTypeMapping(Class<?> clazz, DataType dataType) {
    if (clazz == null || dataType == null) {
      throw new IllegalArgumentException("Class and DataType cannot be null");
    }
    CUSTOM_TYPE_MAPPINGS.put(clazz, dataType);
    // 清除缓存，确保下次检测时使用最新的映射
    clearCache();
  }

  /**
   * 移除自定义类型映射
   *
   * @param clazz 要移除的Java类型
   * @return 如果映射存在并被移除，则返回true；否则返回false
   */
  public static boolean removeCustomTypeMapping(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    boolean removed = CUSTOM_TYPE_MAPPINGS.remove(clazz) != null;
    if (removed) {
      // 清除缓存
      clearCache();
    }
    return removed;
  }

  /** 清除类型检测缓存 */
  public static void clearCache() {
    TYPE_DETECTION_CACHE.clear();
  }

  /**
   * 创建内置类型映射表
   *
   * @return 不可修改的类型映射表
   */
  private static Map<Class<?>, DataType> createTypeMappings() {
    Map<Class<?>, DataType> map = new HashMap<>();

    // 布尔类型映射
    map.put(Boolean.class, DataType.BOOLEAN);
    map.put(boolean.class, DataType.BOOLEAN);

    // 数值类型映射
    map.put(Number.class, DataType.NUMBER);
    map.put(Integer.class, DataType.NUMBER);
    map.put(Long.class, DataType.NUMBER);
    map.put(Double.class, DataType.NUMBER);
    map.put(Float.class, DataType.NUMBER);
    map.put(Short.class, DataType.NUMBER);
    map.put(Byte.class, DataType.NUMBER);
    map.put(BigDecimal.class, DataType.NUMBER);
    map.put(BigInteger.class, DataType.NUMBER);
    map.put(int.class, DataType.NUMBER);
    map.put(long.class, DataType.NUMBER);
    map.put(double.class, DataType.NUMBER);
    map.put(float.class, DataType.NUMBER);
    map.put(short.class, DataType.NUMBER);
    map.put(byte.class, DataType.NUMBER);

    // 日期类型映射
    map.put(Date.class, DataType.DATE);
    map.put(java.sql.Date.class, DataType.DATE);
    map.put(java.sql.Time.class, DataType.DATE);
    map.put(java.sql.Timestamp.class, DataType.DATE);
    map.put(java.time.LocalDate.class, DataType.DATE);
    map.put(java.time.LocalTime.class, DataType.DATE);
    map.put(java.time.LocalDateTime.class, DataType.DATE);
    map.put(java.time.ZonedDateTime.class, DataType.DATE);
    map.put(java.time.OffsetDateTime.class, DataType.DATE);
    map.put(java.time.temporal.Temporal.class, DataType.DATE);

    // JSON类型映射
    map.put(Map.class, DataType.JSON);
    map.put(List.class, DataType.JSON);
    map.put(Collection.class, DataType.JSON);
    map.put(Object[].class, DataType.JSON);
    map.put(com.fasterxml.jackson.databind.JsonNode.class, DataType.JSON);

    return Collections.unmodifiableMap(map);
  }
}
