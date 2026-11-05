package com.bone.claim.domain.model.decision;

import com.bone.claim.domain.model.rule.event.ClaimRulesEvaluatedEvent;
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

  /**
   * 落库置信度的来源受控码（{@link AiConfidenceSource}；{@code null} = 本列加入前的历史行或未落结论）。
   *
   * <p><b>为什么必须落库</b>：{@link #aiConfidence} 在不同链路下装的是不同性质的数（模型自报 / 规则路径 /
   * 桩常数），消费方只看数值无法区分——阈值语义会失真（桩永远同一常数 ⇒ 看起来「永远压线」）。推导在 {@link #complete} 时点完成：降级 ⇒ FALLBACK、模型标识归因见
   * {@link AiConfidenceSource#derive}。
   */
  @Column(name = "confidence_source")
  private String confidenceSource;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "duration_ms")
  private Integer durationMs;

  /**
   * 分诊受控原因码（{@code DecisionTriage.Outcome.reason}：RISK_HIT / AMOUNT_OVER_LIMIT / AI_CONFIDENCE_LOW
   * 等； {@code null} = 执行异常中止未经分诊，无原因可记）。
   *
   * <p><b>为什么必须落库</b>：结论（{@code decision_result}）只说「落点」，原因码才说「为什么」。此前原因只存在于
   * 决策响应里，读侧（快捷赔付风险闸、核赔工作台、对账不变量 5 处置归因）一律看不见——例如"RISK_HIT 命中风控转人工"
   * 的案件，事后无法从库内证明它曾被风控拦截，快捷赔付闸也只能拦"建议拒赔"而拦不住"风控命中"。 与 {@link #consumptionReason}
   * 同理：取决于执行当时的事实，事后重算推不出来。
   */
  @Column(name = "triage_reason")
  private String triageReason;

  /**
   * 自动通过结论的消费结果（受控码；{@code null} = 本次执行没有可消费的自动通过结论）。
   *
   * <p>为什么要落库：消费结果取决于<b>执行当时</b>的开关与单笔上限，事后用当前配置重算只会得到 「现在重跑会怎样」，而不是「当时为什么没放款」。这个字段此前只存在于 {@code
   * POST /decision/execute} 的响应里，读侧一律看不见，于是「已自动通过但资金没动」只能靠翻日志。
   */
  @Column(name = "consumption_reason")
  private String consumptionReason;

  /** 结论消费（或受控跳过）的判定时间（与 {@link #finishedAt} 分离：决策跑完 ≠ 消费判定完）。 */
  @Column(name = "consumed_at")
  private Instant consumedAt;

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

  /** 结束执行并落结论（只允许一次）。未声明置信度来源时按 {@link AiConfidenceSource#UNKNOWN} 落—— 「有数值但证明不了出处」不得冒充模型输出。 */
  public void complete(
      DecisionResult result, DecisionTier tier, BigDecimal confidence, boolean fallback) {
    complete(result, tier, confidence, fallback, AiConfidenceSource.UNKNOWN);
  }

  /** 结束执行并落结论（带置信度溯源，只允许一次）。 */
  public void complete(
      DecisionResult result,
      DecisionTier tier,
      BigDecimal confidence,
      boolean fallback,
      AiConfidenceSource confidenceSource) {
    if (statusEnum() != Status.RUNNING) {
      throw new DomainException("执行已终结，不可重复落结论");
    }
    this.decisionResult = result.name();
    this.decisionTier = tier.name();
    this.aiConfidence = confidence;
    this.confidenceSource =
        confidence == null ? AiConfidenceSource.UNKNOWN.name() : confidenceSource.name();
    this.status = (fallback ? Status.FALLBACK : Status.SUCCESS).name();
    this.finishedAt = Instant.now();
    this.durationMs = (int) (this.finishedAt.toEpochMilli() - this.startedAt.toEpochMilli());
    this.activeFlag = null;
    this.updatedAt = this.finishedAt;
  }

  public AiConfidenceSource confidenceSourceEnum() {
    return confidenceSource == null ? null : AiConfidenceSource.valueOf(confidenceSource);
  }

  /**
   * 回填分诊受控原因码（与结论同一时点落库；异常中止路径无原因可记，保持 NULL）。
   *
   * <p><b>为什么不由 {@link #complete} 一并传参</b>：结论四元组（result/tier/confidence/fallback）是分诊的
   * 「落点」，原因码是分诊的「依据」；执行器在分诊后才调用本方法，异常路径（{@link #fail}）天然跳过。 与 {@link #recordConsumption}
   * 同风格：同一聚合、两个写入动作、各自的守卫。
   */
  public void markTriageReason(String reason) {
    if (isBlank(reason)) {
      throw new DomainException("分诊原因码不得为空（异常中止路径应保持 NULL 而非写空串）");
    }
    if (!isBlank(this.triageReason)) {
      return;
    }
    this.triageReason = reason;
    this.updatedAt = Instant.now();
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

  /**
   * 记录自动通过结论的消费结果（一次执行只消费一次）。
   *
   * <p><b>为什么不由 {@link #complete} 一并记</b>：消费发生在决策跑完之后，且要先读案件、算理算金额、
   * 建结算单——把资金编排塞进「落结论」这一步，等于让决策聚合持有资金能力。两段事实两个时点，故两次写。
   *
   * <p><b>为什么首写为准而不是覆盖</b>：同一执行只有一个消费结论。重复写入相同值无意义，写入不同值
   * 则说明两次判定给出了互相矛盾的答案（要么调用方重试了别的路径，要么并发消费）——那种情况下 后来的值并不更"真"，覆盖只会把第一次的痕迹抹掉。保留首写值，让矛盾在数据里可见。
   *
   * @param reason 受控原因码（{@code APPLIED} 表示已结案建单，其余为受控跳过原因）
   * @throws DomainException 执行仍在 RUNNING（消费不可能先于结论确定），或原因码为空
   */
  public void recordConsumption(String reason) {
    if (statusEnum() == Status.RUNNING) {
      throw new DomainException("执行未终结，不可记录消费结果");
    }
    if (isBlank(reason)) {
      throw new DomainException("消费结果原因码不得为空（无消费动作时应保持 NULL 而非写空串）");
    }
    if (!isBlank(this.consumptionReason)) {
      return;
    }
    this.consumptionReason = reason;
    this.consumedAt = Instant.now();
    this.updatedAt = this.consumedAt;
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

  /**
   * 登记本次执行真实命中的规则（G2-1），随 {@code persist} 的 {@code publishFrom} 发布 {@link
   * ClaimRulesEvaluatedEvent}，由 {@code ClaimRuleHitRecorder} 在 AFTER_COMMIT append 到 {@code
   * t_claim_rule_hit}。
   *
   * <p>为什么由聚合自己发布：基类 {@code addDomainEvent} 是 {@code protected}，应用层无法直接调用—— 这正是决策图里那段 TODO
   * 一直无法落地的技术原因。领域事件由聚合内聚发布，同时守住「一事务一聚合」 与事件归属边界。空命中不发事件（recorder 会跳过，无副作用）。
   */
  public void recordRuleHits(List<ClaimRulesEvaluatedEvent.RuleHit> hits) {
    if (hits == null || hits.isEmpty()) {
      return;
    }
    addDomainEvent(
        new ClaimRulesEvaluatedEvent(
            getTenantId(), getClaimId(), getId(), List.copyOf(hits), Instant.now()));
  }

  /** 节点轨迹（包级可见；理由同 blueprint：findById 不回填集合）。 */
  public List<DecisionTraceNode> traces() {
    return Collections.unmodifiableList(traces);
  }
}
