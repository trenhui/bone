package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 报案仓储（ADR-0030：写侧与读模型合并为单一仓储）。
 *
 * <p>由 {@code @EnableSqlRepositories} 代理实现；查询全部走 SDK Criteria（无需 SQL 模板， 因而不存在「新增方法忘交 .sql 导致启动期
 * TemplateNotFoundException」的风险面）。
 *
 * <p><b>为什么没有 {@code findByReportId}</b>：报案不持有赔案集合，「某报案下的赔案」由 {@code
 * ClaimCaseRepository.findByReportId} 回答——方向固定为「赔案 → 报案」，不建双向依赖。
 */
public interface ClaimReportRepository extends Repository<ClaimReport, Long> {

  /**
   * 按报案号查（对外检索键）。显式带 tenantId 条件，不依赖 SDK 自动租户过滤；跨租户同号不可见。
   *
   * <p>{@code report_no} 全局唯一，理论上可只按号查；这里仍带租户锚点，是为了让「看不到别人的报案」 成为结构保证，而不是依赖调用方先做归属判断。
   */
  default ClaimReport findByReportNo(Long tenantId, String reportNo) {
    return findOneByCriteria(
        Criteria.<ClaimReport>create()
            .eq(ClaimReport::getTenantId, tenantId)
            .eq(ClaimReport::getReportNo, reportNo));
  }
}
