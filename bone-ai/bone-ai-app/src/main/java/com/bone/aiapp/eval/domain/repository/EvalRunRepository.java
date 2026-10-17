package com.bone.aiapp.eval.domain.repository;

import com.bone.aiapp.eval.domain.model.EvalRun;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/** 评测运行仓储（SDK 代理，由 app 的 {@code @EnableSqlRepositories} 自动实现）。 */
public interface EvalRunRepository extends Repository<EvalRun, Long> {

  default List<EvalRun> findByGate(String gate) {
    return QueryBuilder.from(EvalRun.class).where(EvalRun::getGate).eq(gate).list();
  }

  default List<EvalRun> findAll() {
    return QueryBuilder.from(EvalRun.class).list();
  }
}
