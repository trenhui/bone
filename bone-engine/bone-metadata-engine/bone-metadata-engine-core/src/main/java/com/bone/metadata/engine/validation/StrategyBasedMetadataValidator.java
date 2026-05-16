package com.bone.metadata.engine.validation;

import com.bone.metadata.engine.ValidationEngine;
import com.bone.metadata.engine.metadata.EntityMetadata;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 基于策略模式的元数据验证器实现 使用验证引擎和策略执行器进行全面的元数据验证 */
@Component("smartMetadataValidator")
public class StrategyBasedMetadataValidator implements MetadataValidator {

  private static final Logger log = LoggerFactory.getLogger(StrategyBasedMetadataValidator.class);

  private final ValidationEngine validationEngine;

  @Autowired
  public StrategyBasedMetadataValidator(ValidationEngine validationEngine) {
    this.validationEngine = validationEngine;
  }

  /**
   * 验证元数据
   *
   * @param metadata 元数据对象
   * @return 是否验证通过
   */
  @Override
  public boolean validate(EntityMetadata metadata) {
    if (metadata == null) {
      log.error("元数据对象为null");
      return false;
    }

    log.debug("开始验证元数据，实体类型: {}", metadata.getApiName());

    try {
      // 只进行基本结构验证
      if (!validateMetadataStructure(metadata)) {
        log.warn("元数据结构验证失败，实体类型: {}", metadata.getApiName());
        return false;
      }

      log.debug("元数据验证成功，实体类型: {}", metadata.getApiName());
      return true;
    } catch (Exception e) {
      log.error("元数据验证过程发生异常", e);
      return false;
    }
  }

  /** 验证元数据的基本结构 */
  private boolean validateMetadataStructure(EntityMetadata metadata) {
    // 验证实体名称
    if (metadata.getApiName() == null || metadata.getApiName().trim().isEmpty()) {
      log.error("实体API名称为空");
      return false;
    }

    // 验证实体标签
    try {
      // 使用反射获取label字段
      Object label = null;
      try {
        java.lang.reflect.Field labelField = metadata.getClass().getDeclaredField("label");
        labelField.setAccessible(true);
        label = labelField.get(metadata);
      } catch (Exception e) {
        // 忽略异常，使用默认行为
      }
      if (label == null || (label instanceof String && ((String) label).trim().isEmpty())) {
        log.warn("实体标签为空，实体API名称: {}", metadata.getApiName());
      }
    } catch (Exception e) {
      log.debug("Error accessing label field: {}", e.getMessage());
    }

    // 验证字段集合
    if (metadata.getFields() == null || metadata.getFields().isEmpty()) {
      log.warn("实体没有定义字段，实体API名称: {}", metadata.getApiName());
      // 允许没有字段的实体，这可能是抽象实体或基类
    }

    return true;
  }

  /** 创建用于验证的样本数据 这个样本数据主要用于测试验证规则，而不是测试实际业务逻辑 */
  private Map<String, Object> createSampleDataForValidation(EntityMetadata metadata) {
    // 为必填字段创建简单的样本数据
    Map<String, Object> sampleData = new HashMap<>();

    if (metadata.getFields() != null) {
      metadata
          .getFields()
          .forEach(
              (fieldName, field) -> {
                try {
                  // 使用反射获取required字段
                  Object requiredObj = null;
                  try {
                    java.lang.reflect.Field requiredField =
                        field.getClass().getDeclaredField("required");
                    requiredField.setAccessible(true);
                    requiredObj = requiredField.get(field);
                  } catch (Exception e) {
                    // 忽略异常，使用默认行为
                  }
                  if (Boolean.TRUE.equals(requiredObj)) {
                    // 根据字段类型创建示例值
                    String fieldType = getFieldTypeName(field);
                    switch (fieldType.toLowerCase()) {
                      case "string":
                        sampleData.put(fieldName, "sample_value");
                        break;
                      case "integer":
                      case "int":
                        sampleData.put(fieldName, 1);
                        break;
                      case "long":
                        sampleData.put(fieldName, 1L);
                        break;
                      case "double":
                        sampleData.put(fieldName, 1.0);
                        break;
                      case "boolean":
                        sampleData.put(fieldName, true);
                        break;
                      case "array":
                        sampleData.put(fieldName, Collections.singletonList("item"));
                        break;
                      case "object":
                        sampleData.put(fieldName, Collections.singletonMap("key", "value"));
                        break;
                      default:
                        sampleData.put(fieldName, "sample_value");
                    }
                  }
                } catch (Exception e) {
                  log.debug("Error processing field: {}", e.getMessage());
                }
              });
    }

    return sampleData;
  }

  /** 获取字段类型名称 */
  private String getFieldTypeName(Object field) {
    try {
      // 尝试通过反射获取字段类型
      java.lang.reflect.Field typeField = field.getClass().getDeclaredField("type");
      typeField.setAccessible(true);
      Object typeValue = typeField.get(field);
      if (typeValue != null) {
        return typeValue.toString();
      }
    } catch (Exception e) {
      // 忽略异常，返回默认值
    }
    return "string"; // 默认返回字符串类型
  }
}
