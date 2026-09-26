package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.drift.ModelDrift;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 模型漂移仓储（G16）。 */
public interface ModelDriftRepository extends Repository<ModelDrift, Long> {

  default List<ModelDrift> findByEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<ModelDrift>create()
            .entityClass(ModelDrift.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  default long countByEntityFieldAndType(
      Long masterDataEntityId, String fieldCode, String driftType) {
    return countByCriteria(
        Criteria.<ModelDrift>create()
            .entityClass(ModelDrift.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("fieldCode", fieldCode)
            .eq("driftType", driftType));
  }
}
