package com.bone.metadata.catalog.domain.gateway;

import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;

/**
 * 物理结构网关：检查/对齐/清理已发布 RUNTIME 实体的物理表结构与 catalog 模型一致性（MVP-11）。
 *
 * <p>领域侧只定义用例语义；DDL 方言、JDBC 细节由基础设施适配。平台当前支持 MySQL（生产）与 H2（兼容 MySQL 模式，测试）。
 *
 * <p>安全边界分两类：① {@code align} 是非破坏性的（缺表建 / 缺列加，幂等，不缩长度、不改类型）；② {@code dropColumn} / {@code
 * dropDriftedColumns} 是<b>破坏性</b>操作，会永久丢失列数据，仅由管理员显式调用，实现须内置护栏（保留列与在用列禁止删除）。
 */
public interface PhysicalStructureGateway {

  /** 按已发布 RUNTIME 实体模型 diff 物理库，产出对齐计划（纯只读；不执行任何 DDL）。 */
  PhysicalStructurePlan inspect(long tenantId, String entityCode);

  /** 执行对齐计划（CREATE TABLE / ADD COLUMN）。实现须幂等：结构与模型一致时为 no-op 返回执行了 0 条。 */
  PhysicalStructurePlan align(long tenantId, String entityCode);

  /**
   * 发布前结构校验：检测「模型字段类型」与「物理列类型」不兼容的漂移（如模型改为数值但物理列为 VARCHAR）。
   *
   * <p>非破坏性的 {@code align} 只能「缺表建 / 缺列加」，无法修正类型漂移，故在 {@code align} 之前拦截， 把运行期才暴露的 SQL
   * 错误前移为可诊断的发布期拒绝。无漂移时为 no-op；缺表 / 缺列不在此拦截（由 align 补齐）。
   */
  void validateForPublish(long tenantId, String entityCode);

  /**
   * 删除单个物理列（破坏性，须管理员显式调用）。
   *
   * <p>护栏：保留列（id/tenant_id/version/deleted）与「仍在模型中引用的活动字段列」禁止删除，实现须返回 {@code REFUSED} 而非执行 DDL。
   *
   * @return 执行结果；未执行（列不存在 / 被拒）时 {@code executed=0}。
   */
  PhysicalStructurePlan dropColumn(long tenantId, String entityCode, String fieldCode);

  /**
   * 清理物理表中「已不再被模型引用」的孤儿列（破坏性，须管理员显式调用）。
   *
   * <p>仅丢弃物理列名不在当前活动字段集合、且非保留列（id/tenant_id/version/deleted）的列；不触碰在用列，不缩长度、不改类型。
   */
  PhysicalStructurePlan dropDriftedColumns(long tenantId, String entityCode);
}
