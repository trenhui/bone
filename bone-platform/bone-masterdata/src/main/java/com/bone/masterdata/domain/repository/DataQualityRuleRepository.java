package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 数据质量规则仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（Criteria）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化
 * DSL。
 */
public interface DataQualityRuleRepository extends Repository<DataQualityRule, Long> {

  /** 按规则名称统计数量（创建规则时唯一性校验，ADR-0030）。 */
  default long countByName(RuleName name) {
    return countByCriteria(
        Criteria.<DataQualityRule>create()
            .entityClass(DataQualityRule.class)
            .eq("name", name.value()));
  }

  /** 按实体 ID 查询其下全部数据质量规则（读模型，ADR-0030）。 */
  default List<DataQualityRule> findByMasterDataEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<DataQualityRule>create()
            .entityClass(DataQualityRule.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }
}
