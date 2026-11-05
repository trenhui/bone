package com.bone.claim.domain.model.evaluation;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 独立盲标任务（G4-1 数据飞轮的防反馈环闸门，多租户）。
 *
 * <p><b>为什么后督抽检替代不了它</b>：后督抽检员在流程内能看到 AI 建议（这正是抽检的价值——对着 AI 的结论挑错）， 但因此其结论<b>不能</b>当金标准：人工复核受 AI
 * 建议影响（Sculley《Hidden Technical Debt》feedback loops）， 偏差会被当成 ground truth 放大。独立盲标要求标注人在<b>不看任何 AI
 * 痕迹</b>的前提下独立重判， 才能产出「人 vs AI」的可信成对样本。本聚合把这条纪律做成结构，而不是靠标注人自觉。
 *
 * <p><b>状态机</b>：{@code PENDING}（已派发未提交）→ {@code LABELED}（已提交盲标结论）→ {@code UNBLINDED}
 * （已解盲，终态）。终态不可变——解盲后的成对样本是校准/评测的审计证据。
 *
 * <p><b>四道防反馈环闸</b>（每道都有对应测试与变异验证）：
 *
 * <ul>
 *   <li><b>载荷禁区</b>：{@link #FORBIDDEN_PAYLOAD_KEYS} 列出一切 AI 痕迹键名，派发载荷含任一即拒（白名单可增、禁区不可绕）；
 *   <li><b>物理分离</b>：AI 建议快照在解盲时才从决策轨迹读取写入（{@code ai_suggestion_json}），提交前<b>库内根本没有 AI
 *       数据</b>——不是「不让看」而是「无物可看」；
 *   <li><b>一次提交</b>：{@code submit} 仅允许 {@code PENDING} 态调用一次，结论不可改写（要修正另开新任务）；
 *   <li><b>标注人去污染</b>：派发时若该案既有抽检单的质检人与标注人相同，拒绝——同一人先对着 AI 建议检过、再「盲标」， 盲标已被既有印象污染。
 * </ul>
 *
 * <p><b>一致率不在这里算</b>：{@code PAIRED} 只表示「成对样本已产出」，人工结论与 AI 建议的语义一致性由评测流水线离线判定（同一个 blind label
 * 对，可能因判定规则升级而重算一致率）。 在解盲点写死 agree/disagree 等于把判定规则冻结进事务里。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_blind_label_task")
public class BlindLabelTask extends TenantAggregateRoot<Long> {

  /** 派发载荷禁区键（AI 痕迹）：派发载荷出现任一键名即拒绝。新增禁区键不需要迁移——这是代码纪律不是列结构。 */
  public static final Set<String> FORBIDDEN_PAYLOAD_KEYS =
      Set.of(
          "aiSuggestion",
          "aiConfidence",
          "aiAvailable",
          "aiBasis",
          "confidence",
          "confidenceSource",
          "ruleConfidence",
          "ruleHits",
          "triageReason",
          "decisionResult",
          "decisionTier",
          "modelVersion",
          "fallback",
          "knowledgeBasis");

  /** 载荷长度上限（text 列容量内的保守值）。 */
  public static final int MAX_PAYLOAD_LENGTH = 8000;

  /** 人工结论长度上限。 */
  public static final int MAX_RESULT_LENGTH = 2000;

  /** AI 建议快照长度上限。 */
  public static final int MAX_AI_SNAPSHOT_LENGTH = 2000;

  @Column(name = "task_no", nullable = false)
  private String taskNo;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "assignee_id", nullable = false)
  private String assigneeId = "";

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "label_payload_json", nullable = false)
  private String labelPayloadJson;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash = "";

  @Column(name = "label_result_json")
  private String labelResultJson;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "ai_suggestion_json")
  private String aiSuggestionJson;

  @Column(name = "agreement")
  private String agreement;

  @Column(name = "unblinded_at")
  private Instant unblindedAt;

  @Version
  @Column(name = "version")
  private Long version;

  // 审计列：基类不含审计字段，SDK 只映射 @Column 字段——漏声明会让 INSERT 不带
  // created_at/updated_at，而 DDL 这两列 NOT NULL 无默认值，派发必然 1366/1364。
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * 派发盲标任务（PENDING）。
   *
   * @param payloadHash 载荷指纹（sha256 hex64，由应用层对已通过禁区校验的载荷计算；派发即冻结）
   */
  public static BlindLabelTask open(
      Long id,
      Long tenantId,
      String taskNo,
      Long claimId,
      String assigneeId,
      String labelPayloadJson,
      String payloadHash) {
    if (taskNo == null || taskNo.isBlank()) {
      throw new DomainException("盲标任务号不能为空");
    }
    if (claimId == null) {
      throw new DomainException("盲标必须关联案件");
    }
    if (assigneeId == null || assigneeId.isBlank()) {
      throw new DomainException("标注人不能为空");
    }
    if (labelPayloadJson == null || labelPayloadJson.isBlank()) {
      throw new DomainException("标注载荷不能为空");
    }
    if (labelPayloadJson.length() > MAX_PAYLOAD_LENGTH) {
      throw new DomainException("标注载荷超长（上限 " + MAX_PAYLOAD_LENGTH + " 字符）");
    }
    if (payloadHash == null || !payloadHash.matches("^[0-9a-f]{64}$")) {
      throw new DomainException("载荷指纹必须为 sha256 hex64");
    }
    return new BlindLabelTask(
        id, tenantId, taskNo, claimId, assigneeId, labelPayloadJson, payloadHash);
  }

  /** 提交盲标结论（PENDING → LABELED；仅允许一次）。 */
  public void submit(String labelResultJson, Instant now) {
    if (Status.LABELED.name().equals(status) || Status.UNBLINDED.name().equals(status)) {
      throw new DomainException("盲标结论已提交，不可改写（需修正请另开新任务）");
    }
    if (labelResultJson == null || labelResultJson.isBlank()) {
      throw new DomainException("盲标结论不能为空");
    }
    if (labelResultJson.length() > MAX_RESULT_LENGTH) {
      throw new DomainException("盲标结论超长（上限 " + MAX_RESULT_LENGTH + " 字符）");
    }
    this.labelResultJson = labelResultJson;
    this.submittedAt = now;
    this.status = Status.LABELED.name();
  }

  /**
   * 解盲（LABELED → UNBLINDED，终态）。
   *
   * @param aiSuggestionJson AI 建议快照（应用层此时才从决策轨迹读取；AI 未参与时为 null）
   * @param agreement 配对结果（{@code PAIRED}=成对样本已产出 / {@code AI_ABSENT}=该案 AI 未真实参与）
   * @return 解盲领域事件（由应用层 publishFrom 发布）
   */
  public BlindLabelUnblindedEvent unblind(
      String aiSuggestionJson, Agreement agreement, Instant now) {
    if (!Status.LABELED.name().equals(status)) {
      throw new DomainException("仅已提交盲标结论的任务可解盲（当前 " + status + "）");
    }
    if (agreement == null) {
      throw new DomainException("解盲必须给出配对结果（PAIRED/AI_ABSENT）");
    }
    if (aiSuggestionJson != null && aiSuggestionJson.length() > MAX_AI_SNAPSHOT_LENGTH) {
      throw new DomainException("AI 建议快照超长（上限 " + MAX_AI_SNAPSHOT_LENGTH + " 字符）");
    }
    this.aiSuggestionJson = aiSuggestionJson;
    this.agreement = agreement.name();
    this.unblindedAt = now;
    this.status = Status.UNBLINDED.name();
    BlindLabelUnblindedEvent event =
        new BlindLabelUnblindedEvent(
            getId(), getTenantId(), claimId, taskNo, Agreement.PAIRED == agreement, now);
    addDomainEvent(event);
    return event;
  }

  public boolean isUnblinded() {
    return Status.UNBLINDED.name().equals(status);
  }

  private BlindLabelTask(
      Long id,
      Long tenantId,
      String taskNo,
      Long claimId,
      String assigneeId,
      String labelPayloadJson,
      String payloadHash) {
    setId(id);
    setTenantId(tenantId);
    this.taskNo = taskNo;
    this.claimId = claimId;
    this.assigneeId = assigneeId;
    this.status = Status.PENDING.name();
    this.labelPayloadJson = labelPayloadJson;
    this.payloadHash = payloadHash;
    this.version = 0L;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 任务状态（受控三态）。 */
  public enum Status {
    PENDING,
    LABELED,
    UNBLINDED
  }

  /** 成对结果（受控二态）：一致率由评测流水线离线判定，不在此处下结论。 */
  public enum Agreement {
    PAIRED,
    AI_ABSENT
  }
}
