package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.qualityissue.QualityIssue;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 质量整改工单仓储（G11）。 */
public interface QualityIssueRepository extends Repository<QualityIssue, Long> {

  default List<QualityIssue> findByEntityIdAndStatus(Long masterDataEntityId, String status) {
    Criteria<QualityIssue> criteria =
        Criteria.<QualityIssue>create()
            .entityClass(QualityIssue.class)
            .eq("masterDataEntityId", masterDataEntityId);
    if (status != null && !status.isBlank()) {
      criteria.eq("status", status);
    }
    return findByCriteria(criteria);
  }

  /** 统计实体未关闭（OPEN/FIXED）工单数——发布软门禁数据源。 */
  default long countOpenByEntityId(Long masterDataEntityId) {
    long open =
        countByCriteria(
            Criteria.<QualityIssue>create()
                .entityClass(QualityIssue.class)
                .eq("masterDataEntityId", masterDataEntityId)
                .eq("status", "OPEN"));
    long fixed =
        countByCriteria(
            Criteria.<QualityIssue>create()
                .entityClass(QualityIssue.class)
                .eq("masterDataEntityId", masterDataEntityId)
                .eq("status", "FIXED"));
    return open + fixed;
  }
}
