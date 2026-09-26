package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.reference.ReferenceValue;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 参考数据<strong>平台值</strong>仓储（G15；2026-09-26 overlay 拆分）：聚合非租户作用域，读写不受租户过滤—— 平台值对全体租户可见；租户私有值见
 * {@link TenantReferenceValueRepository}。
 */
public interface ReferenceValueRepository extends Repository<ReferenceValue, Long> {

  default List<ReferenceValue> findBySetId(Long setId) {
    return findByCriteria(
        Criteria.<ReferenceValue>create().entityClass(ReferenceValue.class).eq("setId", setId));
  }

  default long countBySetIdAndValueCode(Long setId, String valueCode) {
    return countByCriteria(
        Criteria.<ReferenceValue>create()
            .entityClass(ReferenceValue.class)
            .eq("setId", setId)
            .eq("valueCode", valueCode));
  }
}
