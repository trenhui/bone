package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.quality.QualityReport;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Collection;
import java.util.List;

/**
 * 质量报告仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化
 * DSL。
 */
public interface QualityReportRepository extends Repository<QualityReport, Long> {

  /** 按质量检查 ID 查询质量报告列表（读模型，ADR-0030）。 */
  default List<QualityReport> findByQualityCheckId(Long qualityCheckId) {
    return QueryBuilder.from(QualityReport.class)
        .where(QualityReport::getQualityCheckId)
        .eq(qualityCheckId)
        .list();
  }

  /**
   * 查询全部质量报告（读模型，ADR-0030）。
   *
   * <p>「不传 qualityCheckId = 查全部」的唯一正确出口：上层若直接把 {@code null} 喂给 {@link #findByQualityCheckId}，DSL
   * 会落成 {@code quality_check_id = NULL}，SQL 三值逻辑下永不命中 ⇒ 即使库里有报告，列表也恒为空。故必须与「有筛选」分支显式分开。
   */
  default List<QualityReport> findAllReports() {
    return QueryBuilder.from(QualityReport.class).list();
  }

  /** 按质量检查 ID 批量查询（读模型，ADR-0030）：避免逐个检查查询造成 N+1。 */
  default List<QualityReport> findByQualityCheckIds(Collection<Long> qualityCheckIds) {
    if (qualityCheckIds == null || qualityCheckIds.isEmpty()) {
      return List.of();
    }
    return QueryBuilder.from(QualityReport.class)
        .where(QualityReport::getQualityCheckId)
        .in(qualityCheckIds)
        .list();
  }
}
