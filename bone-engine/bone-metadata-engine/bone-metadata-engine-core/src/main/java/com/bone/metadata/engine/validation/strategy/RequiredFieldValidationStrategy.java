package com.bone.metadata.engine.validation.strategy;

import com.bone.metadata.engine.metadata.EntityMetadata;
import com.bone.metadata.engine.metadata.SmartFieldMetadata;
import com.bone.metadata.engine.validation.ValidationResult;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 必填字段验证策略 验证实体的必填字段是否都已提供有效值 */
public class RequiredFieldValidationStrategy implements ValidationStrategy {

  private static final Logger log = LoggerFactory.getLogger(RequiredFieldValidationStrategy.class);

  @Override
  public void validate(
      EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
    log.debug("开始验证必填字段，实体类型: {}", entityMetadata.getApiName());

    for (SmartFieldMetadata field : entityMetadata.getFields().values()) {
      String fieldName = field.getApiName();
      String fieldLabel = field.getLabel() != null ? field.getLabel() : fieldName;

      if (field.isRequired()) {
        // 检查字段是否存在
        if (!entityData.containsKey(fieldName)) {
          ValidationResult.ValidationError validationError =
              ValidationResult.ValidationError.builder()
                  .fieldPath(fieldName)
                  .message(String.format("字段 '%s' 是必填项", fieldLabel))
                  .build();
          result.addError(validationError);
          continue;
        }

        // 检查字段值是否为null
        Object value = entityData.get(fieldName);
        if (value == null) {
          ValidationResult.ValidationError validationError =
              ValidationResult.ValidationError.builder()
                  .fieldPath(fieldName)
                  .message(String.format("字段 '%s' 不能为null", fieldLabel))
                  .build();
          result.addError(validationError);
          continue;
        }

        // 检查字符串是否为空
        if (value instanceof String && ((String) value).trim().isEmpty()) {
          ValidationResult.ValidationError validationError =
              ValidationResult.ValidationError.builder()
                  .fieldPath(fieldName)
                  .message(String.format("字段 '%s' 不能为空字符串", fieldLabel))
                  .build();
          result.addError(validationError);
        }
      }
    }

    log.debug("必填字段验证完成，实体类型: {}", entityMetadata.getApiName());
  }

  @Override
  public String getName() {
    return "requiredFieldValidation";
  }
}
