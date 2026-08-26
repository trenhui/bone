package com.bone.metadata.engine.validation.strategy;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.SmartFieldMetadata;
import com.bone.metadata.engine.validation.ValidationResult;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 关联字段验证策略 验证实体的关联字段是否有效 */
public class RelationshipValidationStrategy implements ValidationStrategy {

  private static final Logger log = LoggerFactory.getLogger(RelationshipValidationStrategy.class);
  private final EntityMetadataProvider entityMetadataProvider;

  public RelationshipValidationStrategy(EntityMetadataProvider entityMetadataProvider) {
    this.entityMetadataProvider = entityMetadataProvider;
  }

  @Override
  public void validate(
      EntityMetadata entityMetadata, Map<String, Object> entityData, ValidationResult result) {
    log.debug("开始验证关联字段，实体类型: {}", entityMetadata.getApiName());

    // 验证关联字段
    for (SmartFieldMetadata field : entityMetadata.getFields().values()) {
      String fieldName = field.getApiName();
      String fieldLabel = field.getLabel() != null ? field.getLabel() : fieldName;

      try {
        // 尝试安全地获取关联信息
        Object relationship = getRelationshipInfo(field);
        if (relationship != null
            && entityData.containsKey(fieldName)
            && entityData.get(fieldName) != null) {
          String targetEntity = getTargetEntityName(relationship);
          if (targetEntity != null && !targetEntity.isEmpty()) {
            // 验证目标实体是否存在
            if (entityMetadataProvider != null
                && entityMetadataProvider.getEntityMetadata(targetEntity) == null) {
              ValidationResult.ValidationWarning validationWarning =
                  ValidationResult.ValidationWarning.builder()
                      .fieldPath(fieldName)
                      .message(String.format("字段 '%s' 关联的实体类型 '%s' 未定义", fieldLabel, targetEntity))
                      .build();
              result.addWarning(validationWarning);
            }
          }
        }
      } catch (Exception e) {
        log.debug("验证关联字段时出错，字段: {}", fieldName, e);
        // 仅记录日志，不添加警告，避免干扰正常验证流程
      }
    }

    log.debug("关联字段验证完成，实体类型: {}", entityMetadata.getApiName());
  }

  /** 安全获取关联信息 */
  private Object getRelationshipInfo(SmartFieldMetadata field) {
    try {
      Method method = field.getClass().getMethod("getRelationship");
      method.setAccessible(true);
      return method.invoke(field);
    } catch (Exception e) {
      return null;
    }
  }

  /** 安全获取目标实体名称 */
  private String getTargetEntityName(Object relationship) {
    try {
      Method method = relationship.getClass().getMethod("getTargetEntity");
      method.setAccessible(true);
      Object result = method.invoke(relationship);
      return result != null ? result.toString() : null;
    } catch (Exception e) {
      return null;
    }
  }

  /** 实体元数据提供者接口 用于获取目标实体的元数据定义 */
  public interface EntityMetadataProvider {
    EntityMetadata getEntityMetadata(String entityName);
  }

  @Override
  public String getName() {
    return "relationshipValidation";
  }
}
