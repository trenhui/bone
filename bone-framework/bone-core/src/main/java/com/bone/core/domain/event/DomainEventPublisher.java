package com.bone.core.domain.event;

import com.bone.core.domain.DomainEvent;
import java.util.Collection;

/** 领域事件发布端口（应用层在事务提交后调用）。实现放在 infrastructure。 */
public interface DomainEventPublisher {

  void publish(DomainEvent event);

  void publishAll(Collection<? extends DomainEvent> events);
}
