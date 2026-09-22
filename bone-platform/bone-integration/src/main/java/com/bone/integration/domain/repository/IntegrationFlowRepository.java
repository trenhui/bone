package com.bone.integration.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.FlowStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

public interface IntegrationFlowRepository extends Repository<IntegrationFlow, Long> {

  /**
   * 全租户流程清单（平台运维统计专用）。
   *
   * <p>命名后缀 {@code AllTenants} 不是修饰：它让调用方从签名就能看出这是跨租户读，并由 {@code
   * ArchitectureTest#all_tenant_entry_points_must_be_named_all_tenants} 双向绑定 （命名 ↔
   * `@TenantScope(ALL)` / `disableTenantFilter()` 事实），调用面另由 {@code
   * all_tenants_scan_only_by_registered_callers} 收口。本表当前尚未在实体上声明租户 （见 {@code
   * doc/architecture/tenant-entity-baseline.json}），所以本方法的显式跨租户语义
   * 现在就是"提前把意图写清楚"——实体补齐租户声明后，它是唯一允许跨租户的入口。
   */
  default List<IntegrationFlow> findForStatisticsAllTenants() {
    return findByCriteria(Criteria.<IntegrationFlow>create().disableTenantFilter());
  }

  /** 本聚合分页。关键字走 Criteria 全模糊；不关闭租户过滤。 */
  default PageResult<IntegrationFlow> findPage(
      String keyword, FlowStatus status, int pageNum, int pageSize) {
    Criteria<IntegrationFlow> criteria =
        Criteria.<IntegrationFlow>create()
            .like(keyword != null && !keyword.isBlank(), IntegrationFlow::getName, keyword)
            .eq(status != null, IntegrationFlow::getStatus, status)
            .orderByDesc(IntegrationFlow::getId)
            .page(pageNum, pageSize);
    return pageByCriteria(criteria);
  }

  default IntegrationFlow findByName(String name) {
    return findOneByCriteria(Criteria.<IntegrationFlow>create().eq(IntegrationFlow::getName, name));
  }

  /** 当前可见范围内的流程清单（统计页汇总用，不是全租户入口）。 */
  default List<IntegrationFlow> findAll() {
    return findByCriteria(Criteria.<IntegrationFlow>create().orderByDesc(IntegrationFlow::getId));
  }
}
