package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.reference.TenantReferenceValue;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 参考数据<strong>租户私有扩展值</strong>仓储（G15；2026-09-26 overlay 拆分）：租户作用域聚合，SDK 按当前租户
 * 严格过滤/回填——他租户私有值天然不可见，租户经此通道只能触达自己的行。
 */
public interface TenantReferenceValueRepository extends Repository<TenantReferenceValue, Long> {

  default List<TenantReferenceValue> findBySetId(Long setId) {
    return findByCriteria(
        Criteria.<TenantReferenceValue>create()
            .entityClass(TenantReferenceValue.class)
            .eq("setId", setId));
  }

  default long countBySetIdAndValueCode(Long setId, String valueCode) {
    return countByCriteria(
        Criteria.<TenantReferenceValue>create()
            .entityClass(TenantReferenceValue.class)
            .eq("setId", setId)
            .eq("valueCode", valueCode));
  }
}
