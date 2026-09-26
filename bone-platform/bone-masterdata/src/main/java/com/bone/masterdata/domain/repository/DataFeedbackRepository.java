package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.feedback.DataFeedback;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 下游反馈仓储（G17）。 */
public interface DataFeedbackRepository extends Repository<DataFeedback, Long> {

  default List<DataFeedback> findByEntityIdAndStatus(Long masterDataEntityId, String status) {
    Criteria<DataFeedback> criteria =
        Criteria.<DataFeedback>create()
            .entityClass(DataFeedback.class)
            .eq("masterDataEntityId", masterDataEntityId);
    if (status != null && !status.isBlank()) {
      criteria.eq("status", status);
    }
    return findByCriteria(criteria);
  }
}
