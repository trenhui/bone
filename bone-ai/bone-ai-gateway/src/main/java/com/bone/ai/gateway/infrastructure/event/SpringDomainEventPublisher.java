package com.bone.ai.gateway.infrastructure.event;

import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.Collection;
import org.springframework.context.ApplicationEventPublisher;

/** 委托 Spring 事件总线的 {@link DomainEventPublisher} 实现（bone-ai-trace 等订阅方经此收事件）。 */
public class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(DomainEvent event) {
    if (event != null) {
      applicationEventPublisher.publishEvent(event);
    }
  }

  @Override
  public void publishAll(Collection<? extends DomainEvent> events) {
    if (events != null) {
      events.forEach(this::publish);
    }
  }
}
