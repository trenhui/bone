package com.bone.blueprint.domain.gateway;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.metadata.sdk.Repository;

/**
 * 聚合持久化 + 领域事件发布端口（DIP）。写侧 Handler 经此端口完成「保存聚合 + 事务提交前发布领域 事件」，应用层不依赖基础设施实现。
 *
 * <p>实现：{@code infrastructure/persistence/AggregatePersistence}。
 */
public interface AggregatePersister {

  <T extends AggregateRoot<ID>, ID> ID saveAndPublishEvents(
      Repository<T, ID> repository, DomainEventPublisher eventPublisher, T aggregate);

  <T extends AggregateRoot<ID>, ID> void updateAndPublishEvents(
      Repository<T, ID> repository, DomainEventPublisher eventPublisher, T aggregate);
}
