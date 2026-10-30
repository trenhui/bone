package com.bone.claim.application;

import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A3 决策执行应用服务（持久化与回放）。
 *
 * <p>每个写方法只持久化 {@code DecisionExecution} 一个聚合（轨迹为其子实体，级联写入）， 满足 CORE-06「一事务一聚合」。节点执行（可能含 LLM
 * 调用）**不在事务内**——执行器先在内存 聚合轨迹，再以单次短事务落库，避免长事务持锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionExecutionApplicationService {

  private final DecisionExecutionRepository decisionExecutionRepository;
  private final DomainEventPublisher domainEventPublisher;

  /** 落库一次执行（含级联轨迹）。 */
  @Transactional
  public void persist(DecisionExecution execution) {
    decisionExecutionRepository.insert(execution);
    domainEventPublisher.publishFrom(execution);
  }

  /** 查活跃执行（每案至多一条）。 */
  @Transactional(readOnly = true)
  public DecisionExecution findActive(long claimId) {
    return decisionExecutionRepository.findActiveByClaimId(claimId);
  }

  /** 查最近一次执行（含已收敛的；工作台轨迹回放用）。 */
  @Transactional(readOnly = true)
  public DecisionExecution findLatest(long claimId) {
    return decisionExecutionRepository.findLatestByClaimId(claimId);
  }

  /**
   * 取案件最近一次执行的 traceId（无执行记录返回 {@code null}）。
   *
   * <p>对外契约 {@code GET /review/tasks/{id}/trace} 不带 traceId，由服务端解析；返回 null 时调用方须给
   * 404/空结果，而不是回全租户轨迹或最近任意一条（那会泄漏他人案件的决策路径）。
   */
  @Transactional(readOnly = true)
  public String latestTraceId(long claimId) {
    DecisionExecution latest = decisionExecutionRepository.findLatestByClaimId(claimId);
    return latest == null ? null : latest.getTraceId();
  }

  /** 按 traceId 回放全链轨迹。 */
  @Transactional(readOnly = true)
  public List<DecisionTraceNode> replay(String traceId) {
    return decisionExecutionRepository.findTracesByTraceId(traceId);
  }

  /**
   * 记录自动通过结论的消费结果（用本次执行的 {@code traceId} 精确定位）。
   *
   * <p><b>为什么单独一个短事务</b>：这是诊断事实，不是资金动作。它发生在「结案 + 建单」之后，
   * 若与资金动作共用事务，一次乐观锁冲突就会把已建好的结算单一起回滚——用审计信息打断资金链路 是本末倒置。反过来，丢掉这条记录的代价只是「这一单为什么没自动放款」要回去翻日志。
   *
   * <p><b>为什么找不到执行记录只告警不抛</b>：{@code traceId} 取自刚落库的那次执行，正常不会缺失；
   * 缺失说明数据被删或租户上下文漂移，属数据缺陷。此时抛异常会让一个已经完成的资金动作对外表现为 失败（客户端可能重试而重复建单），比丢一条诊断信息更糟。
   */
  @Transactional
  public void recordConsumption(String traceId, String reason) {
    DecisionExecution execution = decisionExecutionRepository.findByTraceId(traceId);
    if (execution == null) {
      log.warn("[决策消费] 未找到执行记录，消费结果未落库: traceId={}, reason={}", traceId, reason);
      return;
    }
    execution.recordConsumption(reason);
    decisionExecutionRepository.update(execution);
  }
}
