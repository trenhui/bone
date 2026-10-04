package com.bone.metadata.sdk.domain.model;

/** 聚合根上的一条级联关系（由 {@code @Cascade} 解析）。 */
public final class CascadeRelation {

  private final String fieldName;
  private final String foreignKeyField;
  private final Class<?> childType;
  private final boolean orphanRemoval;

  public CascadeRelation(
      String fieldName, String foreignKeyField, Class<?> childType, boolean orphanRemoval) {
    this.fieldName = fieldName;
    this.foreignKeyField = foreignKeyField;
    this.childType = childType;
    this.orphanRemoval = orphanRemoval;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getForeignKeyField() {
    return foreignKeyField;
  }

  public Class<?> getChildType() {
    return childType;
  }

  /**
   * 是否在父实体 {@code update}/{@code save} 时清除集合中已不存在的孤儿子行。默认 {@code true}（历史语义）。
   *
   * <p>对「聚合重载不回填子集合」的关系（如订单明细）应为 {@code false}，否则父实体任意一次状态更新都会把 全部既有子行误判为孤儿而清空。
   */
  public boolean isOrphanRemoval() {
    return orphanRemoval;
  }
}
