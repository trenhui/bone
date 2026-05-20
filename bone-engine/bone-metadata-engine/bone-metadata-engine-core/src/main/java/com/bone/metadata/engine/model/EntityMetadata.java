package com.bone.metadata.engine.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 实体元数据模型 注意：此类与org.bone.engine.metadata.model.EntityMetadata存在功能重叠 当前版本保持独立实现，后续可考虑统一元数据模型 */
@Data
public class EntityMetadata {

  /** 实体ID */
  private String id;

  /** 实体名称 */
  private String name;

  /** 实体API名称 */
  private String apiName;

  /** 实体描述 */
  private String description;

  /** 表名 */
  private String tableName;

  /** 字段元数据映射 */
  private Map<String, FieldMetadata> fields = new HashMap<>();

  /** 获取字段元数据映射 */
  public Map<String, FieldMetadata> getFields() {
    return this.fields;
  }

  /** 关系元数据列表 */
  private List<RelationshipMetadata> relationships = new ArrayList<>();

  /** 业务规则列表 */
  private List<BusinessRuleMetadata> businessRules = new ArrayList<>();

  /** 实体类型 */
  private EntityType type;

  /** 是否可扩展 */
  private boolean extensible;

  /** 是否可审计 */
  private boolean auditable;

  /** 创建时间 */
  private Date createdAt;

  /** 更新时间 */
  private Date updatedAt;

  /** 创建人ID */
  private String createdBy;

  /** 更新人ID */
  private String updatedBy;

  /** 版本号 */
  private Integer version;

  /** 扩展属性 */
  private Map<String, Object> extendedProperties;

  /** 实体类型枚举 */
  public enum EntityType {
    STANDARD, // 标准实体
    SMART, // 智能实体
    VIEW // 视图实体
  }

  /** 获取主键字段 */
  public FieldMetadata getPrimaryKeyField() {
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    return fields.values().stream().filter(FieldMetadata::isPrimaryKey).findFirst().orElse(null);
  }

  /** 根据API名称获取字段 */
  public FieldMetadata getFieldByApiName(String apiName) {
    if (fields == null || apiName == null) {
      return null;
    }
    // 遍历Map查找匹配的API名称
    for (FieldMetadata field : fields.values()) {
      if (apiName.equals(field.getApiName())) {
        return field;
      }
    }
    return null;
  }

  /** 获取显示名称字段 */
  public FieldMetadata getDisplayNameField() {
    if (fields == null || fields.isEmpty()) {
      return null;
    }
    // 遍历Map查找显示名称字段
    for (FieldMetadata field : fields.values()) {
      if (field.isDisplayName()) {
        return field;
      }
    }
    return null;
  }

  /** 获取API名称 */
  public String getApiName() {
    return this.apiName;
  }

  /** 获取验证规则列表（兼容方法） */
  public List<?> getValidationRules() {
    return new ArrayList<>();
  }

  /** 获取标签列表（兼容方法） */
  public Map<String, String> getTags() {
    return new HashMap<>();
  }

  /** 获取领域（兼容方法） */
  public String getDomain() {
    return null;
  }

  /** 获取实体类型（兼容方法） */
  public String getEntityType() {
    return null;
  }

  /** 获取关系列表 */
  public List<RelationshipMetadata> getRelationships() {
    return relationships != null ? relationships : List.of();
  }

  /** 获取业务规则列表 */
  public List<BusinessRuleMetadata> getBusinessRules() {
    return businessRules != null ? businessRules : List.of();
  }

  /** 根据名称获取字段 */
  public FieldMetadata getField(String fieldName) {
    if (fields == null || fieldName == null) {
      return null;
    }
    return fields.get(fieldName);
  }
}
