package com.bone.metadata.engine.runtime.security;

import com.bone.metadata.engine.domain.core.SmartBaseEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 字段级安全过滤器，用于根据用户权限过滤实体字段 */
@Component
public class FieldLevelSecurityFilter {

  /**
   * 根据用户可读字段列表过滤实体字段
   *
   * @param entity 需要过滤的实体
   * @param readableFields 用户可读的字段列表
   * @return 过滤后的实体
   */
  public <T extends SmartBaseEntity> T filterFields(T entity, List<String> readableFields) {
    if (entity == null || readableFields == null || readableFields.isEmpty()) {
      return entity;
    }

    // 如果可读字段列表包含"*"，表示可以读取所有字段，不需要过滤
    if (readableFields.contains("*")) {
      return entity;
    }

    // 过滤额外字段
    if (entity instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> entityMap = (Map<String, Object>) entity;
      filterMapFields(entityMap, readableFields);
    }

    // 注意：对于实体类的固定字段，这里无法直接过滤，因为它们是通过getter/setter访问的
    // 实际项目中，可能需要使用反射或其他方式来动态过滤字段
    // 或者返回一个只包含可读字段的DTO对象

    return entity;
  }

  /**
   * 过滤Map类型实体的字段
   *
   * @param entityMap Map类型的实体
   * @param readableFields 用户可读的字段列表
   */
  private void filterMapFields(Map<String, Object> entityMap, List<String> readableFields) {
    // 创建一个副本以避免在迭代时修改Map
    Map<String, Object> fieldsToRemove = new java.util.HashMap<>();

    // 找出所有不在可读字段列表中的字段
    for (String fieldName : entityMap.keySet()) {
      if (!readableFields.contains(fieldName)) {
        fieldsToRemove.put(fieldName, entityMap.get(fieldName));
      }
    }

    // 移除不可读的字段
    for (String fieldName : fieldsToRemove.keySet()) {
      entityMap.remove(fieldName);
    }
  }
}
