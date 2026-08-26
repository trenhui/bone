package com.bone.metadata.engine.domain.model;

import java.util.Map;
import lombok.Data;

/**
 * 关系元数据模型 注意：此类与org.bone.engine.metadata.model.RelationshipMetadata存在功能重叠 当前版本保持独立实现，后续可考虑统一元数据模型
 */
@Data
public class RelationshipMetadata {

  /** 关系ID */
  private String id;

  /** 关系名称 */
  private String name;

  /** 关系API名称 */
  private String apiName;

  /** 关系描述 */
  private String description;

  /** 关系类型 */
  private RelationshipType type;

  /** 源实体 */
  private String sourceEntity;

  /** 目标实体 */
  private String targetEntity;

  /** 源字段 */
  private String sourceField;

  /** 目标字段 */
  private String targetField;

  /** 关系名称 */
  private String relationshipName;

  /** 反向关系名称 */
  private String inverseRelationshipName;

  /** 是否级联删除 */
  private boolean cascadeDelete;

  /** 是否级联更新 */
  private boolean cascadeUpdate;

  /** 是否级联保存 */
  private boolean cascadeSave;

  /** 是否必须存在 */
  private boolean required;

  /** 级联类型 */
  private CascadeType cascadeType;

  /** 加载策略 */
  private FetchType fetchType;

  /** UI配置 */
  private UIConfig uiConfig;

  /** 扩展属性 */
  private Map<String, Object> extendedProperties;

  /** 关系类型枚举 */
  public enum RelationshipType {
    ONE_TO_ONE, // 一对一
    ONE_TO_MANY, // 一对多
    MANY_TO_ONE, // 多对一
    MANY_TO_MANY // 多对多
  }

  /** 级联类型枚举 */
  public enum CascadeType {
    NONE, // 不级联
    ALL, // 全部级联
    PERSIST, // 级联保存
    MERGE, // 级联合并
    REMOVE, // 级联删除
    REFRESH, // 级联刷新
    DETACH // 级联分离
  }

  /** 加载策略枚举 */
  public enum FetchType {
    LAZY, // 延迟加载
    EAGER // 立即加载
  }

  /** UI配置 */
  @Data
  public static class UIConfig {
    private String componentType;
    private Map<String, Object> props;
    private Integer order;
    private boolean hidden;
  }

  /** 判断是否为一对多关系 */
  public boolean isOneToMany() {
    return type == RelationshipType.ONE_TO_MANY;
  }

  /** 判断是否为多对一关系 */
  public boolean isManyToOne() {
    return type == RelationshipType.MANY_TO_ONE;
  }

  /** 判断是否为一对一关系 */
  public boolean isOneToOne() {
    return type == RelationshipType.ONE_TO_ONE;
  }

  /** 判断是否为多对多关系 */
  public boolean isManyToMany() {
    return type == RelationshipType.MANY_TO_MANY;
  }

  /** 获取关系的另一端实体 */
  public String getOppositeEntity(String currentEntity) {
    if (sourceEntity.equals(currentEntity)) {
      return targetEntity;
    } else if (targetEntity.equals(currentEntity)) {
      return sourceEntity;
    }
    return null;
  }

  /** 获取关系名称 */
  public String getName() {
    return name;
  }

  /** 获取关系API名称 */
  public String getApiName() {
    return apiName;
  }
}
