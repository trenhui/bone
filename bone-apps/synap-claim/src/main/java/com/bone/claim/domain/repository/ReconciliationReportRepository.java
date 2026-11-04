package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reconciliation.ReconTenantRef;
import com.bone.claim.domain.model.reconciliation.ReconciliationReport;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.LocalDate;
import java.util.List;

/**
 * 资金对账报告仓储（§11.7，append-only）。
 *
 * <p>{@link #findTenantIdsAllTenants()} 为全租户枚举（供 {@code ReconciliationJob} 逐租户 {@code runAs}），仅
 * {@code adapter.schedule} 可调用（ADR-0030 守卫）。
 */
public interface ReconciliationReportRepository extends Repository<ReconciliationReport, Long> {

  /** 需对账的租户（额度账户 ∪ 结算单的 distinct tenant）。全租户入口，仅 schedule 调用。 */
  @TenantScope(TenantScopeMode.ALL)
  List<ReconTenantRef> findTenantIdsAllTenants();

  /** 同日同通道报告是否已生成（重跑幂等：已生成则跳过，不重复写入）。 */
  default ReconciliationReport findReport(Long tenantId, LocalDate bizDate, String channel) {
    Criteria<ReconciliationReport> criteria =
        Criteria.<ReconciliationReport>create()
            .eq(ReconciliationReport::getTenantId, tenantId)
            .eq(ReconciliationReport::getBizDate, bizDate)
            .eq(ReconciliationReport::getChannel, channel);
    return findOneByCriteria(criteria);
  }

  /** 某业务日全部通道的报告（§10.2 对账报告查询端点：QUOTA/PAYMENT/CASE_SETTLE 一次取齐）。 */
  default List<ReconciliationReport> findReportsByBizDate(Long tenantId, LocalDate bizDate) {
    return findByCriteria(
        Criteria.<ReconciliationReport>create()
            .eq(ReconciliationReport::getTenantId, tenantId)
            .eq(ReconciliationReport::getBizDate, bizDate));
  }
}
