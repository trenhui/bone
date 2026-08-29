package com.bone.blueprint.infrastructure.persistence;

import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.metadata.sdk.Repository;
import org.springframework.stereotype.Component;

/** 聚合持久化 + 领域事件发布适配器（基础设施实现，经 {@link AggregatePersister} 端口暴露）。 */
@Component
public class AggregatePersistence implements AggregatePersister {

  @Override
  public <T extends AggregateRoot<ID>, ID> ID saveAndPublishEvents(
      Repository<T, ID> repository, DomainEventPublisher eventPublisher, T aggregate) {
    ID id = repository.save(aggregate);
    eventPublisher.publishAll(aggregate.getDomainEvents());
    aggregate.clearDomainEvents();
    return id;
  }

  @Override
  public <T extends AggregateRoot<ID>, ID> void updateAndPublishEvents(
      Repository<T, ID> repository, DomainEventPublisher eventPublisher, T aggregate) {
    repository.save(aggregate);
    eventPublisher.publishAll(aggregate.getDomainEvents());
    aggregate.clearDomainEvents();
  }
}
