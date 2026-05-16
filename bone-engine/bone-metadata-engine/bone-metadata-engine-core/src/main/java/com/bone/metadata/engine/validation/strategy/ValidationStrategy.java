package com.bone.metadata.engine.validation.strategy;

import com.bone.metadata.engine.metadata.EntityMetadata;
import com.bone.metadata.engine.validation.ValidationResult;
import java.util.Map;

/** 验证策略接口 使用策略模式定义不同类型的验证规则 */
public interface ValidationStrategy {

  /**
   * 验证实体数据
   *
   * @param entityMetadata 实体元数据
   * @param entityData 实体数据
   * @param result 验证结果对象，用于收集错误和警告
   */
  void validate(
      EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result);

  /**
   * 获取验证策略的名称
   *
   * @return 策略名称
   */
  String getName();
}
