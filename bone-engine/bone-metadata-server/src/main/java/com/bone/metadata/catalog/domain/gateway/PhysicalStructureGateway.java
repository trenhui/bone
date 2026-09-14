package com.bone.metadata.catalog.domain.gateway;

import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;

/**
 * 物理结构网关：检查/对齐已发布 RUNTIME 实体的物理表结构与 catalog 模型一致性（MVP-11）。
 *
 * <p>领域侧只定义「检查 + 执行对齐」两个用例语义；DDL 方言、JDBC 细节由基础设施适配。平台当前支持 MySQL（生产）与 H2（兼容 MySQL
 * 模式，测试）。加列不破坏既有数据；不改类型/删列/缩长度等破坏性变更一概不做（安全第一）。
 */
public interface PhysicalStructureGateway {

  /** 按已发布 RUNTIME 实体模型 diff 物理库，产出对齐计划（纯只读；不执行任何 DDL）。 */
  PhysicalStructurePlan inspect(long tenantId, String entityCode);

  /** 执行对齐计划（CREATE TABLE / ADD COLUMN）。实现须幂等：结构与模型一致时为 no-op 返回执行了 0 条。 */
  PhysicalStructurePlan align(long tenantId, String entityCode);
}
