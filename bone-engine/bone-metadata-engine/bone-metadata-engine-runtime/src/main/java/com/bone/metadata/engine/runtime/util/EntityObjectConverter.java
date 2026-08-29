package com.bone.metadata.engine.runtime.util;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.SmartFieldMetadata;
import com.bone.metadata.engine.runtime.MetadataEngine;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/** 实体对象转换工具 基于元数据引擎实现实体对象与Map之间的自动转换 替代手动编写的convertToMap和updateFromMap方法 */
@Component
public class EntityObjectConverter {

  private static final Logger logger = LoggerFactory.getLogger(EntityObjectConverter.class);

  private final MetadataEngine metadataEngine;

  @Autowired
  public EntityObjectConverter(MetadataEngine metadataEngine) {
    this.metadataEngine = metadataEngine;
  }

  /**
   * 将实体对象转换为Map 使用元数据定义自动映射字段
   *
   * @param entity 实体对象
   * @return Map形式的数据
   */
  public Map<String, Object> convertToMap(Object entity) {
    Assert.notNull(entity, "Entity must not be null");

    try {
      // 获取实体类名作为实体名称
      String entityName = entity.getClass().getSimpleName();
      // 尝试从元数据引擎获取实体元数据
      EntityMetadata metadata = null;
      try {
        metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
      } catch (Exception e) {
        logger.warn("Failed to get metadata for {}", entityName, e);
      }

      Map<String, Object> result = new HashMap<>();

      // 如果有元数据，使用元数据定义的字段
      if (metadata != null && metadata.getFields() != null) {
        for (Map.Entry<String, SmartFieldMetadata> entry : metadata.getFields().entrySet()) {
          SmartFieldMetadata fieldMetadata = entry.getValue();
          String fieldName = fieldMetadata.getName();
          Object value = getFieldValue(entity, fieldName);

          // 使用apiName作为Map的key（如果有）
          String mapKey =
              fieldMetadata.getApiName() != null ? fieldMetadata.getApiName() : fieldName;

          if (value != null) {
            // 递归处理复杂对象
            if (isComplexType(value) && !(value instanceof Collection)) {
              result.put(mapKey, convertToMap(value));
            } else if (value instanceof Collection) {
              result.put(mapKey, convertCollectionToMap((Collection<?>) value));
            } else {
              result.put(mapKey, value);
            }
          }
        }
      } else {
        // 如果没有元数据，反射处理所有字段
        processAllFields(entity, result);
      }

      return result;
    } catch (Exception e) {
      logger.error("Failed to convert entity to map", e);
      throw new RuntimeException("Entity conversion failed", e);
    }
  }

  /**
   * 从Map更新实体对象
   *
   * @param entity 目标实体对象
   * @param data 源Map数据
   * @return 更新后的实体对象
   */
  public <T> T updateFromMap(T entity, Map<String, Object> data) {
    Assert.notNull(entity, "Entity must not be null");
    Assert.notNull(data, "Data map must not be null");

    try {
      // 获取实体类名作为实体名称
      String entityName = entity.getClass().getSimpleName();
      // 尝试从元数据引擎获取实体元数据
      EntityMetadata metadata = null;
      try {
        metadata = (EntityMetadata) metadataEngine.getEntityMetadata(entityName);
      } catch (Exception e) {
        logger.warn("Failed to get metadata for {}", entityName, e);
      }

      // 创建字段名到apiName的映射（用于反向查找）
      Map<String, String> apiNameToFieldName = new HashMap<>();
      if (metadata != null && metadata.getFields() != null) {
        for (Map.Entry<String, SmartFieldMetadata> entry : metadata.getFields().entrySet()) {
          SmartFieldMetadata fieldMetadata = entry.getValue();
          if (fieldMetadata.getApiName() != null) {
            apiNameToFieldName.put(fieldMetadata.getApiName(), fieldMetadata.getName());
          }
        }
      }

      // 处理Map中的每个键值对
      for (Map.Entry<String, Object> entry : data.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        // 查找对应的字段名（优先使用apiName映射）
        String fieldName = apiNameToFieldName.getOrDefault(key, key);

        // 设置字段值
        setFieldValue(entity, fieldName, value);
      }

      return entity;
    } catch (Exception e) {
      logger.error("Failed to update entity from map", e);
      throw new RuntimeException("Entity update failed", e);
    }
  }

  /**
   * 将Map转换为实体对象
   *
   * @param data 源Map数据
   * @param entityClass 目标实体类
   * @return 转换后的实体对象
   */
  public <T> T convertToEntity(Map<String, Object> data, Class<T> entityClass) {
    Assert.notNull(data, "Data map must not be null");
    Assert.notNull(entityClass, "Entity class must not be null");

    try {
      // 创建实体实例
      T entity = entityClass.getDeclaredConstructor().newInstance();
      // 使用updateFromMap更新字段值
      return updateFromMap(entity, data);
    } catch (Exception e) {
      logger.error("Failed to convert map to entity", e);
      throw new RuntimeException("Entity conversion failed", e);
    }
  }

  /** 将实体对象集合转换为Map集合 */
  private List<Map<String, Object>> convertCollectionToMap(Collection<?> collection) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : collection) {
      if (item != null) {
        if (isComplexType(item)) {
          result.add(convertToMap(item));
        } else {
          // 简单类型直接添加
          Map<String, Object> simpleItemMap = new HashMap<>();
          simpleItemMap.put("value", item);
          result.add(simpleItemMap);
        }
      }
    }
    return result;
  }

  /** 处理对象的所有字段（反射方式） */
  private void processAllFields(Object entity, Map<String, Object> result) {
    Class<?> clazz = entity.getClass();
    while (clazz != null && clazz != Object.class) {
      Field[] fields = clazz.getDeclaredFields();
      for (Field field : fields) {
        // 跳过静态字段和transient字段
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
            || java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
          continue;
        }

        field.setAccessible(true);
        try {
          Object value = field.get(entity);
          if (value != null) {
            // 递归处理复杂对象
            if (isComplexType(value) && !(value instanceof Collection)) {
              result.put(field.getName(), convertToMap(value));
            } else if (value instanceof Collection) {
              result.put(field.getName(), convertCollectionToMap((Collection<?>) value));
            } else {
              result.put(field.getName(), value);
            }
          }
        } catch (Exception e) {
          logger.warn("Failed to get field value for {}", field.getName(), e);
        }
      }
      clazz = clazz.getSuperclass();
    }
  }

  /** 获取字段值（支持getter方法） */
  private Object getFieldValue(Object entity, String fieldName) {
    try {
      // 先尝试通过getter方法获取
      String getterName =
          "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
      try {
        Method getter = entity.getClass().getMethod(getterName);
        return getter.invoke(entity);
      } catch (NoSuchMethodException e) {
        // 如果是boolean类型，尝试is方法
        if (fieldName.startsWith("is")) {
          try {
            Method isMethod = entity.getClass().getMethod(fieldName);
            return isMethod.invoke(entity);
          } catch (NoSuchMethodException ignored) {
          }
        }
        // 最后尝试直接访问字段
        Field field = ReflectionUtils.findField(entity.getClass(), fieldName);
        if (field != null) {
          field.setAccessible(true);
          return field.get(entity);
        }
      }
    } catch (Exception e) {
      logger.warn("Failed to get value for field: {}", fieldName, e);
    }
    return null;
  }

  /** 设置字段值（支持setter方法） */
  private void setFieldValue(Object entity, String fieldName, Object value) {
    try {
      // 查找字段
      Field field = ReflectionUtils.findField(entity.getClass(), fieldName);
      if (field == null) {
        return;
      }

      // 如果值为null，不处理
      if (value == null) {
        return;
      }

      // 尝试通过setter方法设置
      String setterName =
          "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
      try {
        Method setter = entity.getClass().getMethod(setterName, field.getType());
        // 类型转换
        Object convertedValue = convertValueToType(value, field.getType());
        setter.invoke(entity, convertedValue);
      } catch (NoSuchMethodException e) {
        // 直接访问字段
        field.setAccessible(true);
        Object convertedValue = convertValueToType(value, field.getType());
        field.set(entity, convertedValue);
      }
    } catch (Exception e) {
      logger.warn("Failed to set value for field: {}, value: {}", fieldName, value, e);
    }
  }

  /** 值类型转换 */
  private Object convertValueToType(Object value, Class<?> targetType) {
    if (value == null || targetType.isInstance(value)) {
      return value;
    }

    // 处理字符串到基本类型的转换
    if (value instanceof String) {
      String strValue = (String) value;

      if (targetType == Integer.class || targetType == int.class) {
        return Integer.parseInt(strValue);
      } else if (targetType == Long.class || targetType == long.class) {
        return Long.parseLong(strValue);
      } else if (targetType == Double.class || targetType == double.class) {
        return Double.parseDouble(strValue);
      } else if (targetType == Boolean.class || targetType == boolean.class) {
        return Boolean.parseBoolean(strValue);
      } else if (targetType == Date.class) {
        try {
          return new Date(Long.parseLong(strValue));
        } catch (NumberFormatException e) {
          // 尝试其他日期格式
          return new Date(strValue);
        }
      }
    }

    // 处理Map到实体的转换
    if (value instanceof Map && !Map.class.isAssignableFrom(targetType)) {
      try {
        return convertToEntity((Map<String, Object>) value, targetType);
      } catch (Exception e) {
        logger.warn("Failed to convert map to entity: {}", targetType.getName(), e);
      }
    }

    // 处理集合类型
    if (value instanceof Collection && Collection.class.isAssignableFrom(targetType)) {
      try {
        Collection<?> sourceCollection = (Collection<?>) value;
        Collection<Object> targetCollection = createCollection(targetType);

        // 获取集合的泛型类型
        Type genericType = getCollectionGenericType(targetType);
        if (genericType instanceof Class) {
          Class<?> elementType = (Class<?>) genericType;
          for (Object item : sourceCollection) {
            if (item instanceof Map && !Map.class.isAssignableFrom(elementType)) {
              targetCollection.add(convertToEntity((Map<String, Object>) item, elementType));
            } else {
              targetCollection.add(convertValueToType(item, elementType));
            }
          }
        } else {
          targetCollection.addAll(sourceCollection);
        }
        return targetCollection;
      } catch (Exception e) {
        logger.warn("Failed to convert collection: {}", targetType.getName(), e);
      }
    }

    return value;
  }

  /** 判断是否为复杂类型（需要递归处理的类型） */
  private boolean isComplexType(Object value) {
    if (value == null) {
      return false;
    }

    Class<?> clazz = value.getClass();
    // 基本类型、包装类、字符串、日期等简单类型
    if (clazz.isPrimitive()
        || Number.class.isAssignableFrom(clazz)
        || String.class.isAssignableFrom(clazz)
        || Boolean.class.isAssignableFrom(clazz)
        || Date.class.isAssignableFrom(clazz)
        || clazz.isEnum()
        || Collection.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz)) {
      return false;
    }

    // 其他类型视为复杂类型
    return true;
  }

  /** 创建集合实例 */
  private Collection<Object> createCollection(Class<?> collectionType) {
    if (List.class.isAssignableFrom(collectionType)) {
      return new ArrayList<>();
    } else if (Set.class.isAssignableFrom(collectionType)) {
      return new HashSet<>();
    } else if (Collection.class.isAssignableFrom(collectionType)) {
      try {
        return (Collection<Object>) collectionType.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        return new ArrayList<>();
      }
    }
    return new ArrayList<>();
  }

  /** 获取集合的泛型类型 */
  private Type getCollectionGenericType(Class<?> collectionType) {
    // 简化实现，实际项目中可能需要更复杂的逻辑
    return Object.class;
  }
}
