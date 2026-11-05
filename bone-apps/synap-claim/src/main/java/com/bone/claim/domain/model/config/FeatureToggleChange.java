package com.bone.claim.domain.model.config;

import com.bone.claim.domain.model.config.event.FeatureToggleChangedEvent;
import com.bone.core.domain.AggregateRoot;

/**
 * 配置变更载波聚合（领域事件载体）。
 *
 * <p><b>为什么需要它</b>：E-5.4 要求「{@code Repository.save()} 必须配 {@code publishFrom()}」，而 {@code
 * publishFrom} 仅接受 {@code AggregateRoot}。但 {@link FeatureToggle} 的持久化基类是 {@code
 * TenantAbstractEntity}（SDK 据此做租户隔离与审计字段映射），无法同时继承 {@code
 * AggregateRoot}——强行改基类会破坏租户隔离。故用本类作为「配置变更」这一领域事实的<b>载波</b>：构造即挂载 {@link
 * FeatureToggleChangedEvent}，由应用层经 {@code domainEventPublisher.publishFrom(change)} 发布。
 *
 * <p><b>本聚合不落库</b>：它只是事件的载体，其事件才是被风控告警 / 审计订阅 / outbox 消费的对象； 持久化审计仍走 {@code FeatureToggle} 同行的
 * {@code remark} / {@code config_json.lastRollback}（见 {@code FeatureToggleChangedEvent} 的口径说明），
 * 避免为「事件载体」单独建表。
 */
public class FeatureToggleChange extends AggregateRoot<Long> {

  private final FeatureToggleChangedEvent event;

  private FeatureToggleChange(Long id, FeatureToggleChangedEvent event) {
    super.setId(id);
    this.event = event;
    addDomainEvent(event);
  }

  /**
   * 工厂入口（E-6.4：应用层经工厂获得领域对象，禁止直接 {@code new}）。
   *
   * @param id 载波标识（一般取被变更开关行的主键；未知传 null）
   * @param event 待广播的配置变更事件
   */
  public static FeatureToggleChange of(Long id, FeatureToggleChangedEvent event) {
    return new FeatureToggleChange(id, event);
  }

  public FeatureToggleChangedEvent event() {
    return event;
  }
}
