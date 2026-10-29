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
  @Cascade(foreignKey = "executionId", orphanRemoval = false)
  private List<DecisionTraceNode> traces = new ArrayList<>();

  @Version
  @Column(name = "version")
  private Long version;

  // 审计列：基类（TenantAggregateRoot）不含审计字段（见其类注释「审计由子类自行声明」），
  // SDK 只映射 @Column 字段——漏声明会让 INSERT 不带 created_at/updated_at，
  // 而 DDL 这两列 NOT NULL 无默认值，执行决策图必然 1364。
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

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
    this.createdAt = this.startedAt;
    this.updatedAt = this.startedAt;
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
    DecisionTraceNode node =
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
            durationMs);
    // 轨迹随执行聚合落库，租户口径与聚合根一致（claim_decision_trace.tenant_id NOT NULL）
    node.setTenantId(getTenantId());
    this.traces.add(node);
  }

  /**
   * 回填 AI 溯源版本（执行终结前调用）。
   *
   * <p><b>只补空、不覆盖</b>：调用方显式传入的版本（如回归基线固定 {@code m1/p1}）优先，绝不被自报值改写； 仅在字段为空时用 AI 节点自报的真实上游模型 /
   * 提示词契约版本兜底。这是对"V5 表注释称回放与复算必需、但接口默认值让字段恒为空"这一 配置失效债务的修正——溯源字段不得静默留空。
   *
   * @throws DomainException 执行已终结（版本快照属执行期事实，终结后不得改写）
   */
  public void backfillAiVersions(String modelVersion, String promptVersion) {
    if (statusEnum() != Status.RUNNING) {
      throw new DomainException("执行已终结，不可回填版本快照");
    }
    if (isBlank(this.modelVersion) && !isBlank(modelVersion)) {
      this.modelVersion = modelVersion;
    }
    if (isBlank(this.promptVersion) && !isBlank(promptVersion)) {
      this.promptVersion = promptVersion;
    }
  }

  private static boolean isBlank(String v) {
    return v == null || v.isBlank();
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
    this.updatedAt = this.finishedAt;
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
    this.updatedAt = this.finishedAt;
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
