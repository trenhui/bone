package com.bone.metadata.engine.runtime;

import java.util.List;

/** 已发布且为 RUNTIME 交付模式的实体快照（供动态 CRUD 使用） */
public record PublishedRuntimeEntity(
    String entityCode,
    String physicalTableName,
    String primaryKeyColumn,
    long tenantId,
    List<RuntimeFieldColumn> columns,
    /** 状态字段编码（声明式状态机 §4.5）：非空时禁止经 CRUD 直写，唯一合法入口是动作端点（G14）。 */
    String stateField) {

  /** 兼容构造：无状态字段（未声明 state_field 的实体，G14 保护不生效）。 */
  public PublishedRuntimeEntity(
      String entityCode,
      String physicalTableName,
      String primaryKeyColumn,
      long tenantId,
      List<RuntimeFieldColumn> columns) {
    this(entityCode, physicalTableName, primaryKeyColumn, tenantId, columns, null);
  }

  public boolean hasColumn(String code) {
    return columns.stream().anyMatch(c -> c.code().equals(code));
  }
}
