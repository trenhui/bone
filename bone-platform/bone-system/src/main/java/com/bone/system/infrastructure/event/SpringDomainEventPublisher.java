package com.bone.system.infrastructure.event;

import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布实现：委托 Spring 事件总线，由 {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅。
 *
 * <p><b>为什么必须存在</b>：聚合内的 {@code addDomainEvent(...)} 只是把事件积起来，<b>不会自动发出去</b>—— 直到有人在 {@code save}
 * 之后调 {@code publishFrom(aggregate)}。缺了这个 Bean，那些事件会随聚合一起被 GC
 * 掉：编译期无错、测试无错、只在「下游什么都没接到」时才被发现（MEMORY：`save` 与 `publishFrom` 必须成对， 由 {@code
 * applicationSaveMustPairWithPublishOrExempt} 门禁守护）。
 *
 * <p>发布时机由应用层的 {@code save} 之后调用点决定，本类不引入事务语义——「什么时候发」是业务决策，不是机制决策。
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
