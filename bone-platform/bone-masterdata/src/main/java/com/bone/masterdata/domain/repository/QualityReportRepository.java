package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
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

  /** 查询全部质量报告（读模型，ADR-0030）。 */
  default List<QualityReport> findAllReports() {
    return QueryBuilder.from(QualityReport.class).list();
  }
}
