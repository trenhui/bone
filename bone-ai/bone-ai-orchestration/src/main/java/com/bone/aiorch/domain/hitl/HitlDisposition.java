package com.bone.aiorch.domain.hitl;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * HITL 处置链记录（一次裁决动作的不可变留痕，表 {@code hitl_disposition}）。
 *
 * <p>合规语义：「谁在何时做了什么裁决、带什么原因」必须跨请求 / 重启可回放——因此每条 confirm / reject / modify / escalate
 * 都落一行，不在进程内只存内存（2026-10-17 复核修复：此前处置链为 {@code @Transient}，重启即丢，审计承诺落空）。
 *
 * <p>modify 行的 {@code value} 存改判值，与 {@code hitl_item.suggested_value}（原建议值）共同构成 diff 证据。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("hitl_disposition")
public class HitlDisposition extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "item_id")
  private String itemId;

  @Column(name = "action")
  private String action; // confirm / reject / modify / escalate

  @Column(name = "reason_type")
  private String reasonType; // 受控字典（reject/modify 必带）

  @Column(name = "value")
  private String value; // 改判值（modify 时携带）

  @Column(name = "comment")
  private String comment;

  @Column(name = "acted_by")
  private String actedBy;

  @Column(name = "acted_at")
  private LocalDateTime actedAt;

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /** 领域工厂：落一条处置记录（应用服务在裁决动作成功后调用）。 */
  public static HitlDisposition of(
      Long tenantId,
      String itemId,
      String action,
      String reasonType,
      String value,
      String comment,
      String actedBy,
      LocalDateTime actedAt) {
    HitlDisposition d = new HitlDisposition();
    d.setTenantId(tenantId);
    d.itemId = itemId;
    d.action = action;
    d.reasonType = reasonType;
    d.value = value;
    d.comment = comment;
    d.actedBy = actedBy;
    d.actedAt = actedAt;
    LocalDateTime now = LocalDateTime.now();
    d.createdAt = now;
    d.updatedAt = now;
    return d;
  }
}
