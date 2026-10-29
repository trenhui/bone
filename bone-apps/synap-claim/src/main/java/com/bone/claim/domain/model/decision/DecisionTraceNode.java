package com.bone.claim.domain.model.decision;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;

/**
 * 决策节点轨迹（claim_decision_trace，append-only 不可变）。
 *
 * <p>回放单元：按 traceId 取出一次决策执行的全部节点序列（证据 → 解析 → 核责 → 理算 → 决策）， 支撑"一键回放决策路径"。行只 INSERT，不
 * UPDATE/DELETE（审计事实，DB 权限收敛为仅 INSERT/SELECT）。
 */
@Getter
@Table("claim_decision_trace")
public class DecisionTraceNode extends AbstractEntity {

  /** 租户（列 NOT NULL）：轨迹随决策执行落库，从执行聚合继承租户口径，缺列即 1364。 */
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  /** 仅聚合根同包赋值（DecisionExecution#appendTrace）。 */
  void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "execution_id", nullable = false)
  private Long executionId;

  @Column(name = "node_seq", nullable = false)
  private Integer nodeSeq;

  @Column(name = "node_type", nullable = false)
  private String nodeType;

  @Column(name = "node_name", nullable = false)
  private String nodeName;

  /** 输入摘要（≤200 字，禁落原始 PII）。 */
  @Column(name = "input_summary", nullable = false)
  private String inputSummary;

  /** 节点完整输出（JSON 文本）。 */
  @Column(name = "output")
  private String output;

  @Column(name = "action_result", nullable = false)
  private String actionResult;

  @Column(name = "duration_ms", nullable = false)
  private Integer durationMs;

  DecisionTraceNode() {}

  DecisionTraceNode(
      String traceId,
      Long claimId,
      Long executionId,
      Integer nodeSeq,
      DecisionNodeType nodeType,
      String nodeName,
      String inputSummary,
      String output,
      DecisionNodeStatus status,
      String actionResult,
      Integer durationMs) {
    this.traceId = traceId;
    this.claimId = claimId;
    this.executionId = executionId;
    this.nodeSeq = nodeSeq;
    this.nodeType = nodeType.name();
    this.nodeName = nodeName;
    this.inputSummary = inputSummary;
    this.output = output;
    this.actionResult = actionResult == null ? status.name() : actionResult;
    this.durationMs = durationMs == null ? 0 : durationMs;
    // 审计列由基类映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，缺赋值即 1364
    Date now = new Date();
    setCreatedAt(now);
    setUpdatedAt(now);
  }

  public DecisionNodeType nodeTypeEnum() {
    return DecisionNodeType.valueOf(nodeType);
  }
}
