package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.steward.StewardAssignment;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 治理角色指派仓储（G3）。 */
public interface StewardAssignmentRepository extends Repository<StewardAssignment, Long> {

  default List<StewardAssignment> findByEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<StewardAssignment>create()
            .entityClass(StewardAssignment.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  default long countByEntityAccountAndRole(
      Long masterDataEntityId, Long accountId, String roleType) {
    return countByCriteria(
        Criteria.<StewardAssignment>create()
            .entityClass(StewardAssignment.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("accountId", accountId)
            .eq("roleType", roleType));
  }
}
