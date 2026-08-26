package com.bone.metadata.engine.runtime.validation.strategy;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.SmartFieldMetadata;
import com.bone.metadata.engine.runtime.validation.ValidationResult;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 字段约束验证策略 验证字段值是否符合定义的约束条件（长度、范围、正则表达式等） */
public class FieldConstraintValidationStrategy implements ValidationStrategy {

  private static final Logger log =
      LoggerFactory.getLogger(FieldConstraintValidationStrategy.class);

  @Override
  public void validate(
      EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
    log.debug("开始验证字段约束，实体类型: {}", entityMetadata.getApiName());

    for (SmartFieldMetadata field : entityMetadata.getFields().values()) {
      String fieldName = field.getApiName();
      String fieldLabel = field.getLabel() != null ? field.getLabel() : fieldName;
      Object value = entityData.get(fieldName);

      if (value == null) {
        continue;
      }

      // 验证字符串长度和正则表达式
      if (value instanceof String) {
        validateStringConstraints(field, fieldName, fieldLabel, (String) value, result);
      }

      // 验证数值范围
      if (value instanceof Number) {
        validateNumberConstraints(field, fieldName, fieldLabel, (Number) value, result);
      }

      // 验证枚举值
      validatePicklistConstraints(field, fieldName, fieldLabel, value, result);

      // 验证数组大小
      if (value instanceof List) {
        validateArrayConstraints(field, fieldName, fieldLabel, (List<?>) value, result);
      }
    }

    log.debug("字段约束验证完成，实体类型: {}", entityMetadata.getApiName());
  }

  /** 验证字符串约束 */
  private void validateStringConstraints(
      SmartFieldMetadata field,
      String fieldName,
      String fieldLabel,
      String value,
      ValidationResult result) {
    // 验证字符串长度
    Integer maxLength = field.getMaxLength();
    if (maxLength != null && value.length() > maxLength) {
      ValidationResult.ValidationError validationError =
          ValidationResult.ValidationError.builder()
              .fieldPath(fieldName)
              .message(String.format("字段 '%s' 的长度不能超过 %d 个字符", fieldLabel, maxLength))
              .build();
      result.addError(validationError);
    }

    Integer minLength = field.getMinLength();
    if (minLength != null && value.length() < minLength) {
      ValidationResult.ValidationError validationError =
          ValidationResult.ValidationError.builder()
              .fieldPath(fieldName)
              .message(String.format("字段 '%s' 的长度不能少于 %d 个字符", fieldLabel, minLength))
              .build();
      result.addError(validationError);
    }

    // 验证正则表达式
    String regexPattern = field.getRegexPattern();
    if (regexPattern != null) {
      if (!Pattern.matches(regexPattern, value)) {
        ValidationResult.ValidationError validationError =
            ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("字段 '%s' 的值不符合要求的格式", fieldLabel))
                .build();
        result.addError(validationError);
      }
    }
  }

  /** 验证数值约束 */
  private void validateNumberConstraints(
      SmartFieldMetadata field,
      String fieldName,
      String fieldLabel,
      Number value,
      ValidationResult result) {
    double numValue = value.doubleValue();

    Double maxValue = field.getMaxValue();
    if (maxValue != null && numValue > maxValue) {
      ValidationResult.ValidationError validationError =
          ValidationResult.ValidationError.builder()
              .fieldPath(fieldName)
              .message(String.format("字段 '%s' 的值不能大于 %s", fieldLabel, maxValue))
              .build();
      result.addError(validationError);
    }

    Double minValue = field.getMinValue();
    if (minValue != null && numValue < minValue) {
      ValidationResult.ValidationError validationError =
          ValidationResult.ValidationError.builder()
              .fieldPath(fieldName)
              .message(String.format("字段 '%s' 的值不能小于 %s", fieldLabel, minValue))
              .build();
      result.addError(validationError);
    }
  }

  /** 验证选择列表约束 */
  private void validatePicklistConstraints(
      SmartFieldMetadata field,
      String fieldName,
      String fieldLabel,
      Object value,
      ValidationResult result) {
    List<String> picklistValues = getPicklistValues(field);
    if (picklistValues != null && !picklistValues.isEmpty()) {
      String stringValue = value.toString();
      if (!picklistValues.contains(stringValue)) {
        ValidationResult.ValidationError validationError =
            ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(
                    String.format(
                        "字段 '%s' 的值必须是以下之一: %s", fieldLabel, String.join(", ", picklistValues)))
                .build();
        result.addError(validationError);
      }
    }
  }

  /** 验证数组约束 */
  private void validateArrayConstraints(
      SmartFieldMetadata field,
      String fieldName,
      String fieldLabel,
      List<?> value,
      ValidationResult result) {
    try {
      // 尝试获取数组最大/最小元素数量
      Integer maxItems = getPropertyValue(field, "maxItems", Integer.class);
      if (maxItems != null && value.size() > maxItems) {
        ValidationResult.ValidationError validationError =
            ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("字段 '%s' 的数组元素数量不能超过 %d 个", fieldLabel, maxItems))
                .build();
        result.addError(validationError);
      }

      Integer minItems = getPropertyValue(field, "minItems", Integer.class);
      if (minItems != null && value.size() < minItems) {
        ValidationResult.ValidationError validationError =
            ValidationResult.ValidationError.builder()
                .fieldPath(fieldName)
                .message(String.format("字段 '%s' 的数组元素数量不能少于 %d 个", fieldLabel, minItems))
                .build();
        result.addError(validationError);
      }
    } catch (Exception e) {
      log.debug("验证数组约束时出错: {}", e.getMessage());
    }
  }

  /** 安全获取选择列表值 */
  private List<String> getPicklistValues(SmartFieldMetadata field) {
    try {
      // 尝试直接访问picklistValues字段
      Field picklistField = field.getClass().getDeclaredField("picklistValues");
      picklistField.setAccessible(true);
      Object picklistValue = picklistField.get(field);
      if (picklistValue instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>) picklistValue;
        return resultList;
      }
    } catch (Exception e) {
      // 忽略异常，返回空列表
    }
    return null;
  }

  /** 安全获取属性值 */
  private <T> T getPropertyValue(Object obj, String propertyName, Class<T> returnType) {
    try {
      Field field = obj.getClass().getDeclaredField(propertyName);
      field.setAccessible(true);
      Object value = field.get(obj);
      if (returnType.isInstance(value)) {
        return returnType.cast(value);
      }
    } catch (Exception e) {
      // 忽略异常，返回null
    }
    return null;
  }

  @Override
  public String getName() {
    return "fieldConstraintValidation";
  }
}
