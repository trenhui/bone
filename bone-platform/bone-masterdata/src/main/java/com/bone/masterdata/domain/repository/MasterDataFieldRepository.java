package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Collection;
import java.util.List;

/**
 * 主数据字段仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（Criteria）只许出现在本接口（{@code ..domain.repository..} 是 SDK 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化
 * DSL。
 */
public interface MasterDataFieldRepository extends Repository<MasterDataField, Long> {

  /** 按 (实体 ID, 字段名) 统计字段数量（创建字段时唯一性校验）。 */
  default long countByEntityIdAndName(Long masterDataEntityId, FieldName name) {
    return countByCriteria(
        Criteria.<MasterDataField>create()
            .entityClass(MasterDataField.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("name", name));
  }

  /** 按实体 ID 查询字段列表（读模型，ADR-0030）。 */
  default List<MasterDataField> findByMasterDataEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<MasterDataField>create()
            .entityClass(MasterDataField.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  /** 按实体 ID 统计字段数量（实体详情页字段数）。 */
  default long countByMasterDataEntityId(Long masterDataEntityId) {
    return countByCriteria(
        Criteria.<MasterDataField>create()
            .entityClass(MasterDataField.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  /** 按实体 ID 批量查询字段（列表页回填字段数，避免逐个 count 造成 N+1）。 */
  default List<MasterDataField> findByMasterDataEntityIds(Collection<Long> masterDataEntityIds) {
    if (masterDataEntityIds == null || masterDataEntityIds.isEmpty()) {
      return List.of();
    }
    return findByCriteria(
        Criteria.<MasterDataField>create()
            .entityClass(MasterDataField.class)
            .in("masterDataEntityId", masterDataEntityIds));
  }

  /** 查询全部字段（读模型，ADR-0030）。 */
  default List<MasterDataField> findAllFields() {
    return QueryBuilder.from(MasterDataField.class).list();
  }
}
