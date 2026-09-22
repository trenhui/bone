package com.bone.metadata.sdk.domain.model;

/** 聚合根上的一条级联关系（由 {@code @Cascade} 解析）。 */
public final class CascadeRelation {

  private final String fieldName;
  private final String foreignKeyField;
  private final Class<?> childType;

  public CascadeRelation(String fieldName, String foreignKeyField, Class<?> childType) {
    this.fieldName = fieldName;
    this.foreignKeyField = foreignKeyField;
    this.childType = childType;
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
}
