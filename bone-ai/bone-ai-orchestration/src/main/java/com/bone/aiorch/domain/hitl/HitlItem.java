package com.bone.aiorch.domain.hitl;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * HITL 待裁决项（租户聚合根，bone-metadata-sdk 持久化，表 {@code hitl_item}）。
 *
 * <p>AI 编排产出建议后停等人工裁决：五态流转由领域方法强制（不允许非法跳转）。处置链本体落 {@code hitl_disposition} 子表 （应用服务在裁决成功后写库），本类的
 * {@code dispositions} 仅为**当次请求内**的回显视图——跨请求回放走仓储，不依赖它。
 *
 * <p>业界对位：AI Agent 人工终审（Anthropic Effective Agents）——AI 只产出建议，人工裁决后才流入业务状态机；置信度 {@code confidence}
 * 低于阈值（<0.7）强制进 PENDING，杜绝自主执行不可回放。
 */
@Getter
@Setter
@Table("hitl_item")
public class HitlItem extends TenantAggregateRoot<String> {

  /** 影子主键：应用生成 UUID（去雪花生成，与 RuleAsset 同款）。 */
  @Id
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private String id;

  @Column(name = "trace_id")
  private String traceId; // 关联证据链 traceId（LLM 调用回放关联键）

  private String scene; // 场景（如 claim-<claimId>）
  private String suggestion; // AI 建议内容（裁决人可见原文）
  private String suggestedValue; // 建议值（JSON/文本；类型化约定由调用方，禁存 Object 防 SDK 映射歧义）
  private String riskLevel; // HIGH/MEDIUM/LOW

  @Column(name = "confidence")
  private BigDecimal confidence; // 模型置信度 0-1；<0.7 强制进 PENDING

  private HitlStatus status = HitlStatus.PENDING;

  @Column(name = "reason_type")
  private String reasonType; // ReasonType 名称（reject/modify 必带）

  @Column(name = "acted_by")
  private String actedBy; // 最后裁决人

  @Column(name = "acted_at")
  private LocalDateTime actedAt; // 最后裁决时刻

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Transient private final List<Disposition> dispositions = new ArrayList<>();

  public HitlItem() {}

  /** 领域工厂：创建待裁决项（应用服务经工厂获得聚合，不得直接 new）。 */
  public static HitlItem create(
      String tenantId,
      String scene,
      String suggestion,
      String suggestedValue,
      String riskLevel,
      double confidence,
      String traceId) {
    HitlItem item = new HitlItem();
    item.setId(UUID.randomUUID().toString());
    item.setTenantId(tenantId);
    item.traceId = traceId;
    item.scene = scene;
    item.suggestion = suggestion;
    item.suggestedValue = suggestedValue;
    item.riskLevel = riskLevel;
    item.confidence = BigDecimal.valueOf(confidence);
    item.status = HitlStatus.PENDING;
    LocalDateTime now = LocalDateTime.now();
    item.createdAt = now;
    item.updatedAt = now;
    return item;
  }

  /** 人工确认采纳（可带回流原因）。 */
  public Disposition confirm(String actor, ReasonType reasonType) {
    requirePending();
    this.status = HitlStatus.CONFIRMED;
    this.reasonType = reasonType == null ? null : reasonType.name();
    return record(actor, "confirm", reasonType, null, null);
  }

  /** 人工驳回（必带回流原因）。 */
  public Disposition reject(String actor, ReasonType reasonType, String comment) {
    requirePending();
    requireReason(reasonType, "reject");
    this.status = HitlStatus.REJECTED;
    this.reasonType = reasonType.name();
    return record(actor, "reject", reasonType, null, comment);
  }

  /** 人工修正后采纳（改值 + 必带回流原因）。 */
  public Disposition modify(
      String actor, String correctedValue, ReasonType reasonType, String comment) {
    requirePending();
    requireReason(reasonType, "modify");
    this.status = HitlStatus.MODIFIED;
    this.suggestedValue = correctedValue;
    this.reasonType = reasonType.name();
    return record(actor, "modify", reasonType, correctedValue, comment);
  }

  /** 升级人工全程处理（编排超时/超轮触发；与产品侧 FR-205 同名贯通）。 */
  public Disposition escalate(String actor, String comment) {
    if (this.status != HitlStatus.PENDING) {
      throw new IllegalStateException("only PENDING item can be escalated, current=" + status);
    }
    this.status = HitlStatus.ESCALATED;
    return record(actor, "escalate", null, null, comment);
  }

  /**
   * 是否已超时未决（SLA 熔断判据，纯函数：时刻由调用方注入，便于单测穷举）。
   *
   * <p><b>为什么用 {@code createdAt} 起算而不加 {@code deadline_at} 列</b>：SLA 是可调策略（不同险种 / 风险等级时限不同）， 把「创建时刻
   * + 策略时长」存成列会让策略变更后面临历史行口径不一致；反过来只存创建时刻、比较时再叠加当前 SLA， 策略调整对全部存量项一致生效。零 DDL 也是库形态模块的硬约束。
   *
   * <p><b>只对 PENDING 判定</b>：已终态项不升级（升级一个已裁决项是无意义的状态倒流）。 {@code createdAt} 缺失（历史数据 /
   * 测试构造）时保守判「未超时」——宁可漏升级，不可误升级。
   */
  public boolean isOverdue(LocalDateTime now, Duration sla) {
    if (this.status != HitlStatus.PENDING || this.createdAt == null || sla == null) {
      return false;
    }
    return this.createdAt.plus(sla).isBefore(now);
  }

  private void requirePending() {
    if (this.status != HitlStatus.PENDING) {
      throw new IllegalStateException("item already resolved: " + status);
    }
  }

  private void requireReason(ReasonType reasonType, String action) {
    if (reasonType == null) {
      throw new IllegalArgumentException(action + " must carry reason_type (AI 中台 §4)");
    }
  }

  private Disposition record(
      String actor, String action, ReasonType reason, String value, String comment) {
    this.actedBy = actor;
    this.actedAt = LocalDateTime.now();
    Disposition d =
        new Disposition(
            action, reason == null ? null : reason.name(), value, comment, actor, actedAt);
    dispositions.add(d);
    return d;
  }

  /** 处置链条目（可回放：谁在何时做了什么裁决、带什么原因）。 */
  public record Disposition(
      String action,
      String reasonType,
      String value,
      String comment,
      String actor,
      LocalDateTime actedAt) {}

  /** 当次请求内的处置链只读视图（持久化真相以 hitl_disposition 表为准）。 */
  public List<Disposition> dispositionHistory() {
    return Collections.unmodifiableList(dispositions);
  }
}
