package com.bone.masterdata.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 主数据实体仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（Criteria / QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点， 被门禁 {@code
 * domain_no_query_builder} / {@code readSideDslOnlyInQueryLayer} 豁免），应用层与适配器不得直接依赖持久化 DSL。
 */
public interface MasterDataEntityRepository extends Repository<MasterDataEntity, Long> {

  /** 按实体名称统计数量（创建实体时唯一性校验）。 */
  default long countByEntityName(MasterDataEntityName name) {
    return countByCriteria(
        Criteria.<MasterDataEntity>create()
            .entityClass(MasterDataEntity.class)
            .eq("entityName", name));
  }

  /** 按来源元数据实体 ID 查询已转换主数据实体的 ID（尚未转换则返回 null）。 */
  default Long findIdByMetaEntityId(Long metaEntityId) {
    List<MasterDataEntity> list =
        findByCriteria(
            Criteria.<MasterDataEntity>create()
                .entityClass(MasterDataEntity.class)
                .eq("metaEntityId", metaEntityId));
    return list.isEmpty() ? null : list.get(0).getId();
  }

  /** 按实体编码统计数量（模板实例化时 entityCode 唯一性校验）。 */
  default long countByEntityCode(String entityCode) {
    return countByCriteria(
        Criteria.<MasterDataEntity>create()
            .entityClass(MasterDataEntity.class)
            .eq("entityCode", entityCode));
  }

  /** 按分类 + 状态分页查询实体（读模型，ADR-0030）。 */
  default PageResult<MasterDataEntity> pageByCategoryAndStatus(
      String category, MasterDataEntityStatus status, int page, int size) {
    FluentQuery<MasterDataEntity> query = QueryBuilder.from(MasterDataEntity.class);
    if (category != null && !category.isBlank()) {
      query.where(MasterDataEntity::getCategory).eq(category);
    }
    if (status != null) {
      query.where(MasterDataEntity::getStatus).eq(status);
    }
    return query.orderByDesc(MasterDataEntity::getCreatedAt).page(page, size);
  }
}
