package com.bone.aitrace.domain.repository;

import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/** 证据快照仓储（由使用方通过 {@code @EnableSqlRepositories} 自动实现，SDK 代理）。 */
public interface EvidenceSnapshotRepository extends Repository<EvidenceSnapshot, Long> {

  /** 回放查询：按 traceId 取该次 LLM 调用的证据快照（读侧 DSL 收敛到仓储，ADR-0030）。 */
  default List<EvidenceSnapshot> findByTraceId(String traceId) {
    return QueryBuilder.from(EvidenceSnapshot.class)
        .where(EvidenceSnapshot::getTraceId)
        .eq(traceId)
        .orderByDesc(EvidenceSnapshot::getCreatedAt)
        .list();
  }
}
