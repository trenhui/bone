package com.bone.claim.domain.model.decision;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 决策执行聚合根（A3，多租户）。
 *
 * <p>一次执行 = 一个聚合：start → recordTrace → complete。硬不变量：每案件同时仅一个活跃执行 （DB 唯一约束 (claim_id, active_flag)
 * 兜底）；决策结果确定后不可改（只追加）。
 *
 * <p>版本快照（graph/rule/model/prompt/catalog）是复算与回放的前提——同一案件用新规则重跑会得到不同结论， 审计结论必须能回到"当时那套配置"。AI
 * 层只产建议，结论由 {@link DecisionTriage} 确定性计算。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("claim_decision_execution")
public class DecisionExecution extends TenantAggregateRoot<Long> {

  /** 执行状态。 */
  public enum Status {
    RUNNING,
    SUCCESS,
    FALLBACK,
    FAILED
  }

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "graph_version", nullable = false)
  private String graphVersion;

  @Column(name = "rule_version", nullable = false)
  private String ruleVersion;

  @Column(name = "model_version")
  private String modelVersion;

  @Column(name = "prompt_version")
  private String promptVersion;

  @Column(name = "catalog_version")
  private String catalogVersion;

  @Column(name = "status", nullable = false)
  private String status;

  /** 1=活跃执行；完成后置 NULL（唯一约束只约束非 NULL，故同案可留多条历史执行）。 */
  @Column(name = "active_flag")
  private Integer activeFlag;

  @Column(name = "decision_result")
  private String decisionResult;

  @Column(name = "decision_tier")
  private String decisionTier;

  @Column(name = "ai_confidence")
  private BigDecimal aiConfidence;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Transient
  @Cascade(foreignKey = "execution_id", orphanRemoval = false)
  private List<DecisionTraceNode> traces = new ArrayList<>();

  @Version
  @Column(name = "version")
  private Long version;

  private DecisionExecution(
      Long id,
      Long tenantId,
      String traceId,
      Long claimId,
      String graphVersion,
      String ruleVersion,
      String modelVersion,
      String promptVersion,
      String catalogVersion) {
    setId(id);
    setTenantId(tenantId);
    this.traceId = traceId;
    this.claimId = claimId;
    this.graphVersion = graphVersion;
    this.ruleVersion = ruleVersion;
    this.modelVersion = modelVersion == null ? "" : modelVersion;
    this.promptVersion = promptVersion == null ? "" : promptVersion;
    this.catalogVersion = catalogVersion == null ? "" : catalogVersion;
    this.status = Status.RUNNING.name();
    this.activeFlag = 1;
    this.startedAt = Instant.now();
  }

  /** 启动一次执行（活跃唯一：同案已有活跃执行时由 DB 唯一约束拦截）。 */
  public static DecisionExecution start(
      Long id,
      Long tenantId,
      String traceId,
      Long claimId,
      String graphVersion,
      String ruleVersion,
      String modelVersion,
      String promptVersion,
      String catalogVersion) {
    return new DecisionExecution(
        id,
        tenantId,
        traceId,
        claimId,
        graphVersion,
        ruleVersion,
        modelVersion,
        promptVersion,
        catalogVersion);
  }

  /** 追加节点轨迹（只追加；执行已终结则拒——结果确定后不可改写轨迹）。 */
  public void recordTrace(
      int nodeSeq,
      DecisionNodeType nodeType,
      String nodeName,
      String inputSummary,
      String output,
      DecisionNodeStatus nodeStatus,
      String actionResult,
      int durationMs) {
    if (statusEnum() != Status.RUNNING) {
      throw new DomainException("执行已终结，不可追加轨迹（只追加语义）");
    }
    this.traces.add(
        new DecisionTraceNode(
            traceId,
            claimId,
            getId(),
            nodeSeq,
            nodeType,
            nodeName,
            inputSummary,
            output,
            nodeStatus,
            actionResult,
            durationMs));
  }

  /** 结束执行并落结论（只允许一次）。 */
  public void complete(
      DecisionResult result, DecisionTier tier, BigDecimal confidence, boolean fallback) {
    if (statusEnum() != Status.RUNNING) {
      throw new DomainException("执行已终结，不可重复落结论");
    }
    this.decisionResult = result.name();
    this.decisionTier = tier.name();
    this.aiConfidence = confidence;
    this.status = (fallback ? Status.FALLBACK : Status.SUCCESS).name();
    this.finishedAt = Instant.now();
    this.durationMs = (int) (this.finishedAt.toEpochMilli() - this.startedAt.toEpochMilli());
    this.activeFlag = null;
  }

  /** 执行失败（如确定性节点异常中止）：置失败并释放活跃位，结论强制转人工。 */
  public void fail() {
    if (statusEnum() != Status.RUNNING) {
      throw new DomainException("执行已终结，不可重复置失败");
    }
    this.status = Status.FAILED.name();
    this.finishedAt = Instant.now();
    this.durationMs = (int) (this.finishedAt.toEpochMilli() - this.startedAt.toEpochMilli());
    this.activeFlag = null;
    this.decisionResult = DecisionResult.MANUAL_REQUIRED.name();
    this.decisionTier = DecisionTier.MANUAL.name();
  }

  public Status statusEnum() {
    return Status.valueOf(status);
  }

  public DecisionResult decisionResultEnum() {
    return decisionResult == null ? null : DecisionResult.valueOf(decisionResult);
  }

  public DecisionTier decisionTierEnum() {
    return decisionTier == null ? null : DecisionTier.valueOf(decisionTier);
  }

  /** 节点轨迹（包级可见；理由同 blueprint：findById 不回填集合）。 */
  public List<DecisionTraceNode> traces() {
    return Collections.unmodifiableList(traces);
  }
}
