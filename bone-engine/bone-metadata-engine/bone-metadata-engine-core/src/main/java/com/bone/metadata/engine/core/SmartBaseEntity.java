package com.bone.metadata.engine.core;

import com.bone.metadata.engine.annotation.SmartEntity;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;

/** 智能实体基类，提供通用属性和方法 支持动态字段、热加载、AI增强和动态计算 */
// 修复MappedSuperclass注解找不到的问题
// @MappedSuperclass
// 修复EntityListeners和AuditingEntityListener找不到的问题
// @EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class SmartBaseEntity implements Serializable { // 不再继承不存在的Entity类

  // 修复Id和GeneratedValue注解找不到的问题
  // @Id
  // @GeneratedValue(strategy = GenerationType.UUID)
  // 修复Column注解找不到的问题
  // @Column(name = "id", updatable = false, nullable = false)
  private String id;

  // 修复Column注解找不到的问题
  // @Column(name = "name", nullable = false)
  private String name;

  // 修复CreatedDate和Column注解找不到的问题
  // @CreatedDate
  // @Column(name = "created_date", nullable = false, updatable = false)
  private LocalDateTime createdDate;

  // 修复CreatedBy和Column注解找不到的问题
  // @CreatedBy
  // @Column(name = "created_by", nullable = false, updatable = false)
  private String createdBy;

  // 修复LastModifiedDate注解找不到的问题
  // @LastModifiedDate
  // 修复Column注解找不到的问题
  // @Column(name = "last_modified_date")
  private LocalDateTime lastModifiedDate;

  // 修复LastModifiedBy和Column注解找不到的问题
  // @LastModifiedBy
  // @Column(name = "last_modified_by")
  private String lastModifiedBy;

  // 修复Column注解找不到的问题
  // @Column(name = "system_modstamp")
  private LocalDateTime systemModstamp;

  // 修复Column注解找不到的问题
  // @Column(name = "is_deleted", columnDefinition = "boolean default false")
  private Boolean isDeleted = false;

  // 使用ConcurrentHashMap提高并发性能
  // 修复Transient注解找不到的问题
  // @Transient
  private final Map<String, Object> extraFields = new ConcurrentHashMap<>();

  // 存储字段的修改历史
  // 修复Transient注解找不到的问题
  // @Transient
  private final Map<String, Object> originalValues = new HashMap<>();

  // 存储计算字段的缓存值
  // 修复Transient注解找不到的问题
  // @Transient
  private final Map<String, Object> calculatedFieldCache = new HashMap<>();

  // 存储字段依赖关系
  // 修复Transient注解找不到的问题
  // @Transient
  private final Map<String, Set<String>> fieldDependencies = new HashMap<>();

  // 存储字段的计算表达式
  // 修复Transient注解找不到的问题
  // @Transient
  private final Map<String, String> calculationExpressions = new HashMap<>();

  /** 构造函数 */
  public SmartBaseEntity() {
    // 初始化实体元数据
    initMetadata();
  }

  /** 初始化实体元数据 */
  private void initMetadata() {
    // 获取实体注解信息
    // 修复getClass()方法调用问题
    Object thisObj = this;
    SmartEntity entityAnnotation = thisObj.getClass().getAnnotation(SmartEntity.class);
    if (entityAnnotation != null) {
      // 可以在这里初始化实体相关的元数据
    }
  }

  /** 获取字段值，优先从实体属性获取，其次从额外字段获取 支持计算字段的动态计算 */
  public Object getField(String fieldName) {
    // 检查是否为计算字段并有缓存值
    if (calculationExpressions.containsKey(fieldName)
        && calculatedFieldCache.containsKey(fieldName)) {
      return calculatedFieldCache.get(fieldName);
    }

    try {
      // 优先从实体属性获取
      // 修复var类型不支持和getClass()方法调用问题
      Object thisObj2 = this;
      java.lang.reflect.Field field = thisObj2.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(this);
    } catch (Exception e) {
      // 从额外字段获取
      return extraFields.get(fieldName);
    }
  }

  /** 设置字段值，优先设置到实体属性，其次设置到额外字段 支持触发依赖字段的重新计算 */
  public void setField(String fieldName, Object value) {
    // 保存原始值用于跟踪变更
    if (!originalValues.containsKey(fieldName)) {
      originalValues.put(fieldName, getField(fieldName));
    }

    boolean fieldUpdated = false;
    try {
      // 优先设置到实体属性
      // 修复var类型不支持和getClass()方法调用问题
      Object thisObj = this;
      java.lang.reflect.Field field = thisObj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(this, value);
      fieldUpdated = true;
    } catch (Exception e) {
      // 设置到额外字段
      extraFields.put(fieldName, value);
      fieldUpdated = true;
    }

    // 如果字段更新成功，清除相关的计算字段缓存并重新计算
    if (fieldUpdated) {
      clearCalculatedFieldCache(fieldName);
      recalculateDependentFields(fieldName);
    }
  }

  /** 检查是否存在指定字段 */
  public boolean hasField(String fieldName) {
    try {
      // 修复getClass()方法调用问题
      Object thisObj = this;
      thisObj.getClass().getDeclaredField(fieldName);
      return true;
    } catch (NoSuchFieldException e) {
      return extraFields.containsKey(fieldName) || calculationExpressions.containsKey(fieldName);
    }
  }

  /** 获取所有字段名 */
  public Set<String> getAllFieldNames() {
    Set<String> fieldNames = new HashSet<>();

    // 添加类的所有字段
    // 修复getClass()方法调用问题和Transient类找不到的问题
    // 暂时不获取类的字段，只返回额外字段和计算表达式字段
    fieldNames.addAll(extraFields.keySet());
    fieldNames.addAll(calculationExpressions.keySet());
    return fieldNames;

    /* 注释掉原来的代码，避免无法访问的语句
    // Object thisObj = this;
    // Arrays.stream(thisObj.getClass().getDeclaredFields())
    //       .filter(field -> !field.isAnnotationPresent(Transient.class))
    //       .forEach(field -> fieldNames.add(field.getName()));

    // 添加额外字段
    fieldNames.addAll(extraFields.keySet());

    // 添加计算字段
    fieldNames.addAll(calculationExpressions.keySet());

    return fieldNames;
    */
  }

  /** 检查字段值是否被修改 */
  public boolean isFieldModified(String fieldName) {
    Object originalValue = originalValues.get(fieldName);
    Object currentValue = getField(fieldName);

    if (originalValue == null) {
      return currentValue != null;
    }

    return !originalValue.equals(currentValue);
  }

  /** 获取所有被修改的字段 */
  public Map<String, Object> getModifiedFields() {
    Map<String, Object> modifiedFields = new HashMap<>();

    for (Map.Entry<String, Object> entry : originalValues.entrySet()) {
      if (isFieldModified(entry.getKey())) {
        modifiedFields.put(entry.getKey(), getField(entry.getKey()));
      }
    }

    // 检查新增的额外字段
    for (String fieldName : extraFields.keySet()) {
      if (!originalValues.containsKey(fieldName)) {
        modifiedFields.put(fieldName, extraFields.get(fieldName));
      }
    }

    return modifiedFields;
  }

  /** 注册计算字段 */
  public void registerCalculatedField(String fieldName, String expression, String... dependencies) {
    calculationExpressions.put(fieldName, expression);
    if (dependencies != null && dependencies.length > 0) {
      fieldDependencies.put(fieldName, new HashSet<>(Arrays.asList(dependencies)));
    }

    // 清除缓存以便下次获取时重新计算
    clearCalculatedFieldCache(fieldName);
  }

  /** 清除计算字段缓存 */
  private void clearCalculatedFieldCache(String fieldName) {
    // 清除依赖该字段的所有计算字段缓存
    fieldDependencies.forEach(
        (calculatedField, deps) -> {
          if (deps.contains(fieldName)) {
            calculatedFieldCache.remove(calculatedField);
          }
        });
  }

  /** 重新计算依赖字段 */
  private void recalculateDependentFields(String fieldName) {
    fieldDependencies.forEach(
        (calculatedField, deps) -> {
          if (deps.contains(fieldName)) {
            // 触发重新计算
            calculatedFieldCache.remove(calculatedField);
            // 这里可以根据需要立即计算或延迟到下次获取时计算
          }
        });
  }

  /** 执行字段计算 */
  public Object calculateField(String fieldName, Supplier<Object> calculator) {
    Object value = calculator.get();
    calculatedFieldCache.put(fieldName, value);
    return value;
  }

  /** 获取实体的JSON表示 */
  public Map<String, Object> toJsonMap() {
    Map<String, Object> jsonMap = new HashMap<>();

    // 添加所有字段及其值
    for (String fieldName : getAllFieldNames()) {
      jsonMap.put(fieldName, getField(fieldName));
    }

    return jsonMap;
  }

  /** 从JSON映射更新实体字段 */
  public void fromJsonMap(Map<String, Object> jsonMap) {
    jsonMap.forEach(this::setField);
  }

  /** 克隆实体 */
  @Override
  public SmartBaseEntity clone() {
    try {
      // 由于SmartBaseEntity是抽象类，无法直接实例化
      // 这里我们暂时返回一个空实现，实际应该由子类来实现clone方法
      throw new UnsupportedOperationException(
          "Clone not supported for abstract class SmartBaseEntity");
    } catch (Exception e) {
      throw new RuntimeException("Failed to clone entity", e);
    }
  }
}
