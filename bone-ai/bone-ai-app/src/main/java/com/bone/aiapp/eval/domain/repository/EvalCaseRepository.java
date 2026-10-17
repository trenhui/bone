package com.bone.aiapp.eval.domain.repository;

import com.bone.aiapp.eval.domain.model.EvalCase;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/** 评测用例仓储（SDK 代理，由 app 的 {@code @EnableSqlRepositories} 自动实现）。 */
public interface EvalCaseRepository extends Repository<EvalCase, Long> {

  default List<EvalCase> findByScene(String scene) {
    return QueryBuilder.from(EvalCase.class).where(EvalCase::getScene).eq(scene).list();
  }
}
