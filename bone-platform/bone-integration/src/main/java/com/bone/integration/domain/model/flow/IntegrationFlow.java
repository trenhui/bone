package com.bone.integration.domain.model.flow;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
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
public class IntegrationFlow extends AggregateRoot<Long> {
  @Id
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
