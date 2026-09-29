package com.bone.studio.generator.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.data.CodeTemplate;

public interface CodeTemplateRepository extends Repository<CodeTemplate, Long> {

  /** 平台租户 ID：内置模板种子等平台级数据落在该租户。 */
  long PLATFORM_TENANT_ID = 0L;

  /**
   * 按租户分页。{@code CodeTemplate} 不是 {@code TenantAggregateRoot}，SDK 不会自动注入租户条件，
   * 故必须显式过滤，否则列表会跨租户返回全部模板。
   *
   * <p>口径：返回「当前租户 + 平台租户」的模板。平台租户下的内置模板种子（{@code content} 为 NULL， 正文真源是 classpath 的
   * .ftl）对所有租户可见——隔离要挡的是「读到别的租户的数据」， 而不是「看不到平台内置模板」。
   */
  default PageResult<CodeTemplate> findPageByTenant(long tenantId, int pageNo, int pageSize) {
    return pageByCriteria(visibleToTenantCriteria(tenantId).page(pageNo, pageSize));
  }

  /**
   * 「当前租户 OR 平台租户」的查询条件（{@code static} 以便单测直接断言生成的 SQL）。
   *
   * <p>注意 SDK {@code Criteria.or()} 的语义：OR 组作为一个整体条件、与其余条件以 AND 相连 （得到 {@code A AND (B OR
   * C)}）。因此这里<b>不设主条件</b>、只放一个 OR 组， 才能得到顶层 OR：{@code (tenant_id = :t OR tenant_id = 0)}。
   */
  static Criteria<CodeTemplate> visibleToTenantCriteria(long tenantId) {
    return Criteria.<CodeTemplate>create()
        .or(c -> c.eq("tenantId", tenantId).eq("tenantId", PLATFORM_TENANT_ID));
  }
}
