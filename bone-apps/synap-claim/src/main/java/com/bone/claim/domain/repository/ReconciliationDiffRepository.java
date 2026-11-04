package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.projection.PendingDiff;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 对账差异明细仓储（§11.7）。
 *
 * <p>finding 字段 append-only；处置态由 {@link #save} 单向迁移一次（{@code PENDING} → 终态），并发重复处置由 应用层先读后判 +
 * 领域不变量兜底。全历史在 {@code t_reconciliation_action}。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ReconciliationDiffRepository extends Repository<ReconciliationDiff, Long> {

  /**
   * 按 ID 取差异（带租户过滤）。
   *
   * <p>不用基类 {@code findById}：那是不带租户条件的通用主键查询，处置接口拿它会让一个租户的运维改到另一个租户的差异。
   */
  ReconciliationDiff findDiff(@Param("diffId") long diffId);

  /**
   * 待处置队列：本租户<b>没有</b> {@code DIFF_RESOLVE} 处置流水的差异，按发现时间倒序。
   *
   * <p>处置态不落差异行（见 {@code ReconciliationAction} 的类注释），故此处必须 LEFT JOIN 处置流水并按 {@code a.id IS NULL}
   * 筛。只查 {@code resolution = 'PENDING'} 是不够的——那一列在不回写的设计下永远是 PENDING， 查出来会是"全部差异"而不是"待处置的差异"。
   *
   * @param limit 最多返回条数（运营队列按页取用；0 或负数按 1 处理，避免全表扫）
   */
  @TenantScope(TenantScopeMode.AUTO)
  List<PendingDiff> findPending(@Param("limit") int limit);

  /**
   * 取报告下的全部差异明细（§10.2 对账报告查询端点）。
   *
   * <p>与 {@link #findPending} 的分工：那里是「待处置队列」（LEFT JOIN 处置流水），这里是「当日报告的
   * 全量差异」——已处置的也要返回，否则日终报告只看得见没处理的，处置率无从对账。
   */
  default List<ReconciliationDiff> findByReportIds(List<Long> reportIds) {
    if (reportIds == null || reportIds.isEmpty()) {
      return List.of();
    }
    return findByCriteria(
        Criteria.<ReconciliationDiff>create()
            .in(ReconciliationDiff::getReportId, reportIds.toArray()));
  }
}
