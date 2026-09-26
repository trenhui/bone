package com.bone.masterdata.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.masterdata.domain.model.template.DomainTemplate;
import com.bone.masterdata.domain.model.template.valueobject.DomainTemplateStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 域模板仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 被门禁豁免）。
 */
public interface DomainTemplateRepository extends Repository<DomainTemplate, Long> {

  /** 按域编码统计数量（创建模板时唯一性校验）。 */
  default long countByDomainCode(String domainCode) {
    return countByCriteria(
        com.bone.metadata.sdk.query.criteria.Criteria.<DomainTemplate>create()
            .entityClass(DomainTemplate.class)
            .eq("domainCode", domainCode));
  }

  /** 按状态分页查询模板（读模型，ADR-0030）。 */
  default PageResult<DomainTemplate> pageByStatus(DomainTemplateStatus status, int page, int size) {
    FluentQuery<DomainTemplate> query = QueryBuilder.from(DomainTemplate.class);
    if (status != null) {
      query.where(DomainTemplate::getStatus).eq(status);
    }
    return query.orderByDesc(DomainTemplate::getUpdatedAt).page(page, size);
  }

  /** 查询全部可实例化（已发布）模板——租户实例化入口的选择列表。 */
  default List<DomainTemplate> findPublished() {
    return findByCriteria(
        com.bone.metadata.sdk.query.criteria.Criteria.<DomainTemplate>create()
            .entityClass(DomainTemplate.class)
            .eq("status", DomainTemplateStatus.PUBLISHED));
  }
}
