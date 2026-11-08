package com.bone.claim.domain.model.config;

import com.bone.claim.domain.model.config.event.ClaimFlowConfigChangedEvent;
import com.bone.core.domain.AggregateRoot;

/**
 * 流程配置变更载波聚合（E-5.4）。
 *
 * <p><b>为什么需要载波</b>：{@link ClaimFlowConfig} 继承 {@code TenantAbstractEntity}（SDK 依赖它做 租户隔离与审计列映射），而
 * {@code DomainEventPublisher.publishFrom} 只接受 {@link AggregateRoot}。 与 {@link FeatureToggleChange}
 * 同款：不落库，仅承载事件，避免为「事件载体」单独建表或改实体基类。
 */
public class ClaimFlowConfigChange extends AggregateRoot<Long> {

  private final ClaimFlowConfigChangedEvent event;

  private ClaimFlowConfigChange(Long id, ClaimFlowConfigChangedEvent event) {
    super.setId(id);
    this.event = event;
    addDomainEvent(event);
  }

  /** E-6.4：应用层经工厂获得领域对象，禁止直接 {@code new}；id 未知（新建未回填）可传 null。 */
  public static ClaimFlowConfigChange of(Long id, ClaimFlowConfigChangedEvent event) {
    return new ClaimFlowConfigChange(id, event);
  }

  public ClaimFlowConfigChangedEvent event() {
    return event;
  }
}
