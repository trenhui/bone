package com.bone.metadata.sdk.domain.enums;

/** 排序方向枚举类，表示 SQL 查询的排序方向。 */
public enum SortDirection {
  ASC("ASC"),
  DESC("DESC");

  private final String direction;

  SortDirection(String direction) {
    this.direction = direction;
  }

  public String getDirection() {
    return direction;
  }
}
