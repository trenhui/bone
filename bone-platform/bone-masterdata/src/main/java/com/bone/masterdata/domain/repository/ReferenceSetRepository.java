package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.reference.ReferenceSet;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 参考数据值域仓储（G15）。 */
public interface ReferenceSetRepository extends Repository<ReferenceSet, Long> {

  default long countBySetCode(String setCode) {
    return countByCriteria(
        Criteria.<ReferenceSet>create().entityClass(ReferenceSet.class).eq("setCode", setCode));
  }

  default List<ReferenceSet> findByStatus(String status) {
    return findByCriteria(
        Criteria.<ReferenceSet>create().entityClass(ReferenceSet.class).eq("status", status));
  }
}
