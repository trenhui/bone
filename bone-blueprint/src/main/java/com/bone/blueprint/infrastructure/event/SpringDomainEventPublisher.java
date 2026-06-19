package com.bone.blueprint.infrastructure.event;

import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 领域事件发布实现：委托 Spring 事件总线，由 {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅。 */
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
