package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.history.CodeGenerationHistory;
import java.util.List;

public interface CodeGenerationHistoryRepository extends Repository<CodeGenerationHistory, Long> {

  default List<CodeGenerationHistory> findAll() {
    return findByCriteria(Criteria.<CodeGenerationHistory>create());
  }
}
