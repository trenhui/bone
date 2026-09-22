package com.bone.studio.generator.domain.gateway;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import java.util.Optional;

/**
 * 表元数据读侧端口（ADR-0013 / DDD E-4.2）。
 *
 * <p>写侧用例按自然键读取表元数据时经此端口；读侧 DSL（{@code Criteria}）收敛于 infrastructure 实现， 避免命令处理器直接依赖
 * {@code @ReadSideOnly} 类型。
 */
public interface GenTableMetadataReadPort {

  /** 按数据源键 + 原始表名读取表元数据；不存在时返回 {@link Optional#empty()}。 */
  Optional<GenTableMetadata> findByDataSourceKeyAndTableName(
      String dataSourceKey, String originalTableName);
}
