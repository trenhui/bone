package com.bone.claim.infrastructure.event;

import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器（Spring {@link ApplicationEventPublisher} 委托实现，blueprint 同款）。
 *
 * <p>{@code publishFrom(aggregate)} 为端口 default 方法（发布并清空），此处不覆盖； 跨进程/资金事实事件另走
 * outbox（后续迭代），本发布器负责进程内 AFTER_COMMIT 订阅链。
 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void publish(DomainEvent event) {
    applicationEventPublisher.publishEvent(event);
  }

  @Override
  public void publishAll(java.util.Collection<? extends DomainEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    events.forEach(this::publish);
  }
}
