package com.bone.studio.generator.domain.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;

/**
 * 主子聚合（一对多）产物生成器 SPI。
 *
 * <p><b>为何独立于 {@link FileGenerator}</b>：单表产物生成器的签名只有一张表的元数据，而主子聚合服务 （如「订单 +
 * 订单明细一次事务创建」）同时需要主表与子表元数据、以及子表外键列， 三者都是调用方（应用服务）已解析的输入——复用 {@code FileGenerator}
 * 要么扩签名牵连全部实现，要么把关系塞进表元数据污染持久化模型，都不划算。
 *
 * <p>应用服务在「命令携带子表配置」时调用；无实现 Bean 时静默跳过（能力可选，不阻断单表生成主链路）。
 */
public interface AggregateRelationFileGenerator {

  /**
   * 生成主子聚合应用服务。
   *
   * @param parent 主表元数据（已同步）
   * @param child 子表元数据（已同步）
   * @param fkColumn 子表外键列名（物理列名，如 {@code order_id}）
   * @param basePackage 基础包
   * @param moduleName 模块名
   */
  GeneratedFile generate(
      GenTableMetadata parent,
      GenTableMetadata child,
      String fkColumn,
      String basePackage,
      String moduleName);
}
