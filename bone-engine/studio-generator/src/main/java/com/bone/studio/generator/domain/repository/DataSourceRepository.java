package com.bone.studio.generator.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.data.DataSource;

public interface DataSourceRepository extends Repository<DataSource, Long> {

  /**
   * 按租户分页。{@code DataSource} 不是 {@code TenantAggregateRoot}，SDK 不会自动注入租户条件，
   * 故必须显式过滤，否则列表会跨租户返回全部数据源。
   */
  default PageResult<DataSource> findPageByTenant(long tenantId, int page, int size) {
    return pageByCriteria(Criteria.<DataSource>create().eq("tenantId", tenantId).page(page, size));
  }
}
