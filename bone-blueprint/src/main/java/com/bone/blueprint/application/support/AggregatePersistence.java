package com.bone.blueprint.application.support;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.metadata.sdk.Repository;

/** 聚合持久化 + 领域事件发布（事务提交前注册，AFTER_COMMIT 订阅处理）。 */
public final class AggregatePersistence {

  private AggregatePersistence() {}

  public static <T extends AggregateRoot<ID>, ID> ID saveAndPublishEvents(
      Repository<T, ID> repository, DomainEventPublisher eventPublisher, T aggregate) {
    ID id = repository.save(aggregate);
    eventPublisher.publishAll(aggregate.getDomainEvents());
    aggregate.clearDomainEvents();
    return id;
  }

  public static <T extends AggregateRoot<ID>, ID> void updateAndPublishEvents(
      Repository<T, ID> repository, DomainEventPublisher eventPublisher, T aggregate) {
    repository.save(aggregate);
    eventPublisher.publishAll(aggregate.getDomainEvents());
    aggregate.clearDomainEvents();
  }
}
