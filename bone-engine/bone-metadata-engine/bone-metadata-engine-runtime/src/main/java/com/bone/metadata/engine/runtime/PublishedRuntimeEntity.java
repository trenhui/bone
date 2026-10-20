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
    String stateField,
    /** 行级 ACL 策略原始 JSON（meta_entity.row_policies，可空；null = 默认 owner_field=created_by）。 */
    String rowPolicies,
    /** 自动编号配置原始 JSON（meta_entity.auto_number，可空；非空时创建记录由平台生成单据号）。 */
    String autoNumber,
    /**
     * 字段级角色可见性配置（UI 协议 props.roles 的服务端投影，可空；null/空 = 无字段级限制）。
     *
     * <p>由 {@code RuntimeFieldVisibilityProjector} 从发布态 ui-schema 提取；运行期查询/写入路径经 {@link
     * RuntimeFieldVisibilityPolicy} 消费。为 null 时行为与本组件引入前完全一致（零影响升级）。
     */
    List<RuntimeFieldVisibility> fieldVisibilities) {

  /** 完整构造（不含字段可见性）。 */
  public PublishedRuntimeEntity(
      String entityCode,
      String physicalTableName,
      String primaryKeyColumn,
      long tenantId,
      List<RuntimeFieldColumn> columns,
      String stateField,
      String rowPolicies,
      String autoNumber) {
    this(
        entityCode,
        physicalTableName,
        primaryKeyColumn,
        tenantId,
        columns,
        stateField,
        rowPolicies,
        autoNumber,
        null);
  }

  /** 完整构造（不含自动编号与字段可见性）。 */
  public PublishedRuntimeEntity(
      String entityCode,
      String physicalTableName,
      String primaryKeyColumn,
      long tenantId,
      List<RuntimeFieldColumn> columns,
      String stateField,
      String rowPolicies) {
    this(
        entityCode,
        physicalTableName,
        primaryKeyColumn,
        tenantId,
        columns,
        stateField,
        rowPolicies,
        null,
        null);
  }

  /** 兼容构造：无状态字段（未声明 state_field 的实体，G14 保护不生效）。 */
  public PublishedRuntimeEntity(
      String entityCode,
      String physicalTableName,
      String primaryKeyColumn,
      long tenantId,
      List<RuntimeFieldColumn> columns) {
    this(
        entityCode, physicalTableName, primaryKeyColumn, tenantId, columns, null, null, null, null);
  }

  /** 兼容构造：无状态字段 + 无行策略。 */
  public PublishedRuntimeEntity(
      String entityCode,
      String physicalTableName,
      String primaryKeyColumn,
      long tenantId,
      List<RuntimeFieldColumn> columns,
      String stateField) {
    this(
        entityCode,
        physicalTableName,
        primaryKeyColumn,
        tenantId,
        columns,
        stateField,
        null,
        null,
        null);
  }

  public boolean hasColumn(String code) {
    return columns.stream().anyMatch(c -> c.code().equals(code));
  }
}
