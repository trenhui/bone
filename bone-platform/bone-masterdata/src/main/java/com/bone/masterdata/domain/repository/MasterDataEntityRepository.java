package com.bone.masterdata.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
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
    return pageByCategoryStatusAndKeyword(category, status, null, page, size);
  }

  /**
   * 按分类 + 状态 + 关键字分页查询实体（读模型，ADR-0030）。
   *
   * <p><b>为何展开成多支 OR</b>：SDK 的 {@code FluentQuery.or(Consumer)} 分组是桩实现（{@code
   * QueryContext.openGroup} 只压栈、不写括号），无法生成 {@code (a OR b)}。而 SQL 里 {@code A AND B AND x OR y}
   * 会被解析成 {@code (A AND B AND x) OR y}，使分类/状态过滤被关键字旁路。
   *
   * <p>故按布尔分配律展开为 {@code (作用域 AND 名称) OR (作用域 AND 编码) OR (作用域 AND 描述)}—— 每支内部全 AND、支间由首条件的 OR
   * 标记连接，语义等价于 {@code 作用域 AND (名称 OR 编码 OR 描述)}。
   */
  default PageResult<MasterDataEntity> pageByCategoryStatusAndKeyword(
      String category, MasterDataEntityStatus status, String keyword, int page, int size) {
    FluentQuery<MasterDataEntity> query = QueryBuilder.from(MasterDataEntity.class);
    boolean hasCategory = category != null && !category.isBlank();
    if (keyword == null || keyword.isBlank()) {
      applyScope(query, category, status, false);
      return query.orderByDesc(MasterDataEntity::getCreatedAt).page(page, size);
    }
    // 第一支：作用域 + 实体名称
    applyScope(query, category, status, false);
    query.where(MasterDataEntity::getEntityName).contains(keyword);
    // 后续支：作用域首条件以 OR 接入，再拼本支关键字字段
    if (hasCategory || status != null) {
      applyScope(query, category, status, true);
    } else {
      query.or(MasterDataEntity::getEntityCode).contains(keyword);
      query.or(MasterDataEntity::getDescription).contains(keyword);
      return query.orderByDesc(MasterDataEntity::getCreatedAt).page(page, size);
    }
    query.where(MasterDataEntity::getEntityCode).contains(keyword);
    applyScope(query, category, status, true);
    query.where(MasterDataEntity::getDescription).contains(keyword);
    return query.orderByDesc(MasterDataEntity::getCreatedAt).page(page, size);
  }

  /** 追加「分类 + 状态」作用域条件；{@code firstOr=true} 时首条件以 OR 接入（开启新一支）。 */
  private static void applyScope(
      FluentQuery<MasterDataEntity> query,
      String category,
      MasterDataEntityStatus status,
      boolean firstOr) {
    boolean orNext = firstOr;
    if (category != null && !category.isBlank()) {
      Condition<MasterDataEntity, String> cond =
          orNext
              ? query.or(MasterDataEntity::getCategory)
              : query.where(MasterDataEntity::getCategory);
      cond.eq(category);
      orNext = false;
    }
    if (status != null) {
      Condition<MasterDataEntity, MasterDataEntityStatus> cond =
          orNext ? query.or(MasterDataEntity::getStatus) : query.where(MasterDataEntity::getStatus);
      cond.eq(status);
    }
  }
}
