package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.lineage.LineageRecord;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 数据血缘仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化
 * DSL。
 */
public interface LineageRecordRepository extends Repository<LineageRecord, Long> {

  /** 按 source / target 查询血缘边列表（读模型，ADR-0030）。 */
  default List<LineageRecord> findBySourceOrTarget(String sourceEntity, String targetEntity) {
    FluentQuery<LineageRecord> query = QueryBuilder.from(LineageRecord.class);
    if (targetEntity != null && !targetEntity.isBlank()) {
      query.where(LineageRecord::getTargetEntity).eq(targetEntity);
    }
    if (sourceEntity != null && !sourceEntity.isBlank()) {
      query.where(LineageRecord::getSourceEntity).eq(sourceEntity);
    }
    return query.list();
  }
}
