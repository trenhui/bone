package com.bone.integration.domain.model.flow;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import com.bone.integration.domain.model.flow.valueobject.FlowStatus;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_flow")
public class IntegrationFlow extends TenantAggregateRoot<Long> {
  @Id

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  @Deleted
  private Boolean deleted = false;

  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private String description;
  private FlowStatus status;

  public static IntegrationFlow create(Long id, String name, String description) {
    if (name == null || name.isBlank()) {
      throw new DomainException("流程名称不能为空");
    }

    IntegrationFlow flow = new IntegrationFlow();
    flow.id = id;
    flow.name = name;
    flow.description = description;
    flow.status = FlowStatus.DRAFT;
    flow.addDomainEvent(new FlowCreatedEvent(flow));
    return flow;
  }

  public void activate() {
    if (this.status == FlowStatus.ACTIVE) {
      throw new DomainException("流程已激活");
    }
    if (this.status == FlowStatus.DELETED) {
      throw new DomainException("流程已删除，无法激活");
    }
    this.status = FlowStatus.ACTIVE;
    this.addDomainEvent(new FlowActivatedEvent(this));
  }

  public void deactivate() {
    if (this.status == FlowStatus.INACTIVE) {
      throw new DomainException("流程已停用");
    }
    if (this.status == FlowStatus.DELETED) {
      throw new DomainException("流程已删除");
    }
    this.status = FlowStatus.INACTIVE;
  }

  public void update(String name, String description) {
    if (name == null || name.isBlank()) {
      throw new DomainException("流程名称不能为空");
    }
    this.name = name;
    this.description = description;
  }
}
