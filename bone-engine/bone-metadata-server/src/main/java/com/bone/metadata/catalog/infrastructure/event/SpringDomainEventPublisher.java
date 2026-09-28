package com.bone.metadata.catalog.infrastructure.event;

import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布实现（与 bone-system 同款）：委托 Spring 事件总线，由 {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅。
 *
 * <p>本模块聚合（{@code MetaEntity} 等）继承 {@code AbstractEntity} 而非 {@code AggregateRoot} （ADR-0016
 * 有意决策），不积聚事件；应用层在发布用例成功后直接调 {@code publish(event)} 显式发出， 时机（持久化成功后、事务提交前）由调用点决定，本类不引入事务语义。
 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void publish(DomainEvent event) {
    if (event != null) {
      applicationEventPublisher.publishEvent(event);
    }
  }

  @Override
  public void publishAll(Collection<? extends DomainEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    events.forEach(this::publish);
  }
}
