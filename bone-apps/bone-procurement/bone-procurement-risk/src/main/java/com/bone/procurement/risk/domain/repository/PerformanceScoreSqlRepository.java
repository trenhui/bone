package com.bone.procurement.risk.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.risk.domain.model.risk.PerformanceScore;
import java.util.List;

/** 绩效评分写侧仓储：只读投影查询落在 default 方法，不产出授信/淘汰决定。 */
public interface PerformanceScoreSqlRepository extends Repository<PerformanceScore, Long> {

  default List<PerformanceScore> findScores(Long tenantId, String supplierId) {
    return findByCriteria(
        Criteria.<PerformanceScore>create()
            .entityClass(PerformanceScore.class)
            .eq("tenantId", tenantId)
            .eq("supplierId", supplierId));
  }
}
