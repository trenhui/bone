package com.bone.masterdata.domain.model.subscription;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 主数据消费订阅（G10，UC-C1/C3）：应用 × 主数据实体 N:M；PENDING → ACTIVE → REVOKED。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_entity_subscription")
public class EntitySubscription extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  /** 消费应用ID（bone_application.id）。 */
  @Column(name = "app_id")
  private Long appId;

  /** 订阅模式：READ-只读取数 / EVENT-变更事件。 */
  @Column(name = "subscribe_mode")
  private String subscribeMode;

  /** 状态：PENDING / ACTIVE / REVOKED。 */
  private String status;

  @Column(name = "requested_by")
  private Long requestedBy;

  @Column(name = "approved_by")
  private Long approvedBy;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static EntitySubscription request(
      Long id, Long masterDataEntityId, Long appId, String subscribeMode, Long requestedBy) {
    EntitySubscription subscription = new EntitySubscription();
    subscription.id = id;
    subscription.masterDataEntityId = masterDataEntityId;
    subscription.appId = appId;
    subscription.subscribeMode =
        subscribeMode == null || subscribeMode.isBlank() ? "READ" : subscribeMode;
    subscription.status = "PENDING";
    subscription.requestedBy = requestedBy;
    subscription.createdAt = LocalDateTime.now();
    subscription.updatedAt = LocalDateTime.now();
    return subscription;
  }

  /** 批准生效（数据 Owner 执行）。 */
  public void approve(Long approverId) {
    if (!"PENDING".equals(this.status)) {
      throw new DomainException("仅待批准状态的订阅可批准");
    }
    this.status = "ACTIVE";
    this.approvedBy = approverId;
    this.approvedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /** 撤销订阅。 */
  public void revoke() {
    if (!"ACTIVE".equals(this.status)) {
      throw new DomainException("仅已生效的订阅可撤销");
    }
    this.status = "REVOKED";
    this.updatedAt = LocalDateTime.now();
  }
}
