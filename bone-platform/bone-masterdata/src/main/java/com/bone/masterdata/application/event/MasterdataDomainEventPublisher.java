package com.bone.masterdata.application.event;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 主数据领域事件发布器：在聚合持久化后，将 {@link DomainEvent} 通过 Spring 事件机制 路由到 {@link
 * org.springframework.transaction.event.TransactionalEventListener} Handler。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterdataDomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public void publishFrom(AggregateRoot<?> aggregate) {
    if (aggregate == null) {
      return;
    }
    List<DomainEvent> events = new ArrayList<>(aggregate.getDomainEvents());
    aggregate.clearDomainEvents();
    events.forEach(this::publish);
  }

  public void publish(DomainEvent event) {
    if (event == null) {
      return;
    }
    try {
      applicationEventPublisher.publishEvent(event);
    } catch (Exception ex) {
      log.error("主数据领域事件发布失败: type={}", event.getClass().getSimpleName(), ex);
    }
  }
}
