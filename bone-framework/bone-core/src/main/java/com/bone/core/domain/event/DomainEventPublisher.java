package com.bone.core.domain.event;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.DomainEvent;
import java.util.Collection;

/** 领域事件发布端口（应用层在事务提交后调用）。实现放在 infrastructure。 */
public interface DomainEventPublisher {

  void publish(DomainEvent event);

  void publishAll(Collection<? extends DomainEvent> events);

  /**
   * 发布聚合内累积的领域事件并清空（显式版「保存即发布」，等价业界 Spring Data {@code @DomainEvents} +
   * {@code @AfterDomainEventPublication} 的效果）。
   *
   * <p>用法：{@code repository.save(aggregate); eventPublisher.publishFrom(aggregate);}
   *
   * <p>为何显式而不自动：事件发布时机是业务语义（须在持久化成功后、事务提交前），显式调用让团队一眼看到「何时发」， 避免隐藏在 AOP/代理里造成理解成本。
   *
   * @param aggregate 待发布事件的聚合根，为 null 时静默忽略
   */
  default void publishFrom(AggregateRoot<?> aggregate) {
    if (aggregate == null) {
      return;
    }
    publishAll(aggregate.getDomainEvents());
    aggregate.clearDomainEvents();
  }
}
