package com.bone.metadata.engine.runtime;

import java.util.List;

/** 已发布且为 RUNTIME 交付模式的实体快照（供动态 CRUD 使用） */
public record PublishedRuntimeEntity(
    String entityCode,
    String physicalTableName,
    String primaryKeyColumn,
    long tenantId,
    List<RuntimeFieldColumn> columns) {

  public boolean hasColumn(String code) {
    return columns.stream().anyMatch(c -> c.code().equals(code));
  }
}
