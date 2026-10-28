package com.bone.claim.application;

import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A3 决策执行应用服务（持久化与回放）。
 *
 * <p>每个写方法只持久化 {@code DecisionExecution} 一个聚合（轨迹为其子实体，级联写入）， 满足 CORE-06「一事务一聚合」。节点执行（可能含 LLM
 * 调用）**不在事务内**——执行器先在内存 聚合轨迹，再以单次短事务落库，避免长事务持锁。
 */
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
}
