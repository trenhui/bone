package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.history.CodeGenerationHistory;
import java.util.List;

public interface CodeGenerationHistoryRepository extends Repository<CodeGenerationHistory, Long> {

  /** 最近生成优先：{@code findAll()} 返回的是按主键递增的顺序，历史页最新一条会沉在最底下。 */
  default List<CodeGenerationHistory> findRecent() {
    return findByCriteria(
        Criteria.<CodeGenerationHistory>create().orderByDesc(CodeGenerationHistory::getCreatedAt));
  }
}
