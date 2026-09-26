package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.category.MasterDataCategory;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 主数据分类仓储：写能力来自基类 {@link Repository}，读侧以 default 方法承载（ADR-0030）。 */
public interface MasterDataCategoryRepository extends Repository<MasterDataCategory, Long> {

  default List<MasterDataCategory> findByEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<MasterDataCategory>create()
            .entityClass(MasterDataCategory.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  default long countByEntityIdAndCode(Long masterDataEntityId, String code) {
    return countByCriteria(
        Criteria.<MasterDataCategory>create()
            .entityClass(MasterDataCategory.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("code", code));
  }

  /** 统计子分类数（删除前校验）。 */
  default long countByParentId(Long parentCategoryId) {
    return countByCriteria(
        Criteria.<MasterDataCategory>create()
            .entityClass(MasterDataCategory.class)
            .eq("parentCategoryId", parentCategoryId));
  }
}
