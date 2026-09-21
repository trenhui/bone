package com.bone.masterdata.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.masterdata.domain.standard.DataStandard;
import com.bone.masterdata.domain.standard.vo.StandardFieldCode;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 数据标准仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（Criteria / QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK
 * 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化 DSL。
 */
public interface DataStandardRepository extends Repository<DataStandard, Long> {

  /** 按 (实体编码, 字段编码) 统计数量（创建数据标准时唯一性校验）。 */
  default long countByEntityCodeAndFieldCode(String entityCode, StandardFieldCode fieldCode) {
    return countByCriteria(
        Criteria.<DataStandard>create()
            .entityClass(DataStandard.class)
            .eq("entityCode", entityCode)
            .eq("fieldCode", fieldCode));
  }

  /** 按实体编码查询数据标准列表（读模型，ADR-0030）。 */
  default List<DataStandard> findByEntityCode(String entityCode) {
    return findByCriteria(
        Criteria.<DataStandard>create()
            .entityClass(DataStandard.class)
            .eq("entityCode", entityCode));
  }

  /** 按实体编码 + 字段编码关键字分页查询（读模型，ADR-0030）。 */
  default PageResult<DataStandard> pageByEntityCodeAndFieldCodeLike(
      String entityCode, String keyword, int page, int size) {
    FluentQuery<DataStandard> query = QueryBuilder.from(DataStandard.class);
    if (entityCode != null && !entityCode.isBlank()) {
      query.where(DataStandard::getEntityCode).eq(entityCode);
    }
    if (keyword != null && !keyword.isBlank()) {
      query.where(DataStandard::getFieldCode).like(keyword);
    }
    return query.page(page, size);
  }
}
