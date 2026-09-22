package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.quality.QualityCheck;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 质量检查仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化
 * DSL。
 */
public interface QualityCheckRepository extends Repository<QualityCheck, Long> {

  /** 按实体 ID 查询质量检查列表（读模型，ADR-0030）。 */
  default List<QualityCheck> findByMasterDataEntityId(Long masterDataEntityId) {
    return QueryBuilder.from(QualityCheck.class)
        .where(QualityCheck::getMasterDataEntityId)
        .eq(masterDataEntityId)
        .list();
  }

  /** 查询全部质量检查（读模型，ADR-0030）。 */
  default List<QualityCheck> findAllChecks() {
    return QueryBuilder.from(QualityCheck.class).list();
  }
}
