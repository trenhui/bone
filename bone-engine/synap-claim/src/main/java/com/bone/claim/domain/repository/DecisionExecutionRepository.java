package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/** A3 决策执行仓储（执行记录 + 轨迹分开查询：轨迹 append-only 且量大，不随聚合回填）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface DecisionExecutionRepository extends Repository<DecisionExecution, Long> {

  /** 查活跃执行（active_flag=1）；每案至多一条（唯一约束兜底）。 */
  DecisionExecution findActiveByClaimId(@Param("claimId") long claimId);

  /** 按 traceId 取全链轨迹（回放）。 */
  List<DecisionTraceNode> findTracesByTraceId(@Param("traceId") String traceId);
}
