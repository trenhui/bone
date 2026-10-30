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

  /**
   * 查最近一次执行（含已收敛的）。
   *
   * <p>为什么需要：{@code /review/tasks/{id}/trace} 的契约路径不带 traceId，工作台进详情页时执行往往<b>已收敛</b> （{@code
   * active_flag=0}），只按 {@link #findActiveByClaimId} 查会得到 404——「刚跑完就看不了轨迹」。
   */
  DecisionExecution findLatestByClaimId(@Param("claimId") long claimId);

  /**
   * 按 traceId 取执行记录（traceId 是租户内唯一的回放键）。
   *
   * <p>为什么消费结果要用它定位而不是 {@link #findLatestByClaimId}：决策执行结束后 {@code active_flag}
   * 释放，「同案仅一个活跃执行」的约束随之失效——此时若有并发执行起来，latest 可能指向另一次执行， 消费结果就写到别人的行上。traceId 来自本次执行的返回值，是唯一精确的锚点。
   */
  DecisionExecution findByTraceId(@Param("traceId") String traceId);

  /** 按 traceId 取全链轨迹（回放）。 */
  List<DecisionTraceNode> findTracesByTraceId(@Param("traceId") String traceId);
}
