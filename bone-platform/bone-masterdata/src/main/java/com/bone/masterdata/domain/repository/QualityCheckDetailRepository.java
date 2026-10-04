package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.quality.QualityCheckDetail;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Collection;
import java.util.List;

/**
 * 质量检查明细仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化
 * DSL。
 */
public interface QualityCheckDetailRepository extends Repository<QualityCheckDetail, Long> {

  /** 按检查任务 ID 查询明细（读模型，ADR-0030）。 */
  default List<QualityCheckDetail> findByQualityCheckId(Long qualityCheckId) {
    return QueryBuilder.from(QualityCheckDetail.class)
        .where(QualityCheckDetail::getQualityCheckId)
        .eq(qualityCheckId)
        .list();
  }

  /** 按检查任务 ID 批量查询明细（读模型，ADR-0030）：避免逐任务查询造成 N+1。 */
  default List<QualityCheckDetail> findByQualityCheckIds(Collection<Long> qualityCheckIds) {
    if (qualityCheckIds == null || qualityCheckIds.isEmpty()) {
      return List.of();
    }
    return QueryBuilder.from(QualityCheckDetail.class)
        .where(QualityCheckDetail::getQualityCheckId)
        .in(qualityCheckIds)
        .list();
  }

  /**
   * 按记录 ID 查询明细（读模型，ADR-0030）。
   *
   * <p>这是「按记录查质量结果」的真实数据来源：此前该能力只有参数没有实现，recordId 被原样回填到 DTO 而不参与筛选。
   */
  default List<QualityCheckDetail> findByRecordId(Long recordId) {
    return QueryBuilder.from(QualityCheckDetail.class)
        .where(QualityCheckDetail::getRecordId)
        .eq(recordId)
        .list();
  }

  /**
   * 按检查任务 ID 集合 + 记录 ID 交叉查询明细（读模型，ADR-0030）。
   *
   * <p>用于「限定在某个实体的检查范围内按记录过滤」：先由实体定检查任务集合，再与记录求交集，避免跨实体串数据。
   */
  default List<QualityCheckDetail> findByQualityCheckIdsAndRecordId(
      Collection<Long> qualityCheckIds, Long recordId) {
    if (qualityCheckIds == null || qualityCheckIds.isEmpty() || recordId == null) {
      return List.of();
    }
    return QueryBuilder.from(QualityCheckDetail.class)
        .where(QualityCheckDetail::getQualityCheckId)
        .in(qualityCheckIds)
        .and(QualityCheckDetail::getRecordId)
        .eq(recordId)
        .list();
  }
}
