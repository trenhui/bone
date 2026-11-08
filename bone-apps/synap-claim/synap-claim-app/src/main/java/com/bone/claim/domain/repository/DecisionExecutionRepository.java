package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
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

  /**
   * 按收敛时间窗取已完成的执行（评测样本导出的数据源）。
   *
   * <p><b>为什么只取 {@code SUCCESS} / {@code FALLBACK}</b>：{@code RUNNING} 尚未产出结论，{@code FAILED}
   * 是异常中止、没有可比较的决策结果——把这两类混进评测样本会让「AI 准确率」被未完成项稀释。 {@code FALLBACK} 必须保留：它代表 LLM
   * 不可用时规则路径的结论，正是「降级后还准不准」的评测对象。
   *
   * <p><b>为什么上界写死在 SQL（{@code LIMIT 500}）</b>：评测导出是离线批处理入口，不是全库扫描器。 参数化 LIMIT
   * 在不同数据库/驱动上的绑定行为不一致，写死上界 + 服务层再按请求条数截断，能保证 「最坏情况也只读 500 行」，不会因调用方传大数把库拖死。
   *
   * <p>租户条件由 SQL 模板的 tenant 占位符注入，<b>不接受入参租户</b>——防止越权导出他人样本。
   */
  List<DecisionExecution> findConvergedInWindow(
      @Param("from") Instant from, @Param("to") Instant to);

  /**
   * 按落库时间窗取指定节点的轨迹（AI 运行期健康度的数据源）。
   *
   * <p><b>为什么不复用 {@link #findTracesByTraceId}</b>：那个方法按单次执行取全链节点，回答「这次决策怎么走的」； 本方法跨执行取同一类节点，
   * 回答「近期这类节点总共跑了多少次、坏在哪」——前者是回放，后者是统计，取数粒度不同。
   *
   * <p><b>{@code nodeName} 由调用方传入而不是写死在 SQL</b>：节点名唯一真源是 {@code DecisionGraph.AI_ASSESS}。
   * 写死会让改名后查询静默返回空，而「空」 在健康度报表里显示成「近期没有决策」——把常量改名事故伪装成流量不足。
   *
   * <p><b>为什么上界写死在 SQL（{@code LIMIT 2000}）</b>：同 {@link #findConvergedInWindow}，运维端点不得变成全库扫描器；
   * 服务层再按结果是否打满上界标记 {@code truncated}，不静默裁剪。
   */
  List<DecisionTraceNode> findAiAssessNodesInWindow(
      @Param("nodeName") String nodeName, @Param("from") Instant from, @Param("to") Instant to);
}
