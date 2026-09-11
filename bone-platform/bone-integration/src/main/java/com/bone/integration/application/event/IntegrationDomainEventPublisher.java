package com.bone.integration.application.event;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.DomainEvent;
import com.bone.integration.application.event.outbox.IntegrationOutboxWriter;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import com.bone.integration.domain.model.connector.event.ConnectorTestedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import com.bone.integration.domain.model.flow.event.FlowExecutedEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 集成领域事件分发器：在聚合持久化后，将 {@link DomainEvent} 路由到对应应用层 Handler。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationDomainEventPublisher {

  private final IntegrationOutboxWriter outboxWriter;
  private final ConnectorCreatedHandler connectorCreatedHandler;
  private final ConnectorTestedHandler connectorTestedHandler;
  private final FlowCreatedHandler flowCreatedHandler;
  private final FlowActivatedHandler flowActivatedHandler;
  private final FlowExecutedHandler flowExecutedHandler;
  private final ExecutionStartedHandler executionStartedHandler;
  private final ExecutionCompletedHandler executionCompletedHandler;

  public void publishFrom(AggregateRoot<?> aggregate) {
    if (aggregate == null) {
      return;
    }
    List<DomainEvent> events = new ArrayList<>(aggregate.getDomainEvents());
    events.forEach(this::publish);
    aggregate.clearDomainEvents();
  }

  public void publish(DomainEvent event) {
    if (event == null) {
      return;
    }
    outboxWriter.append(event);
    dispatch(event);
  }

  private void dispatch(DomainEvent event) {
    if (event instanceof ConnectorCreatedEvent e) {
      connectorCreatedHandler.handle(e);
    } else if (event instanceof ConnectorTestedEvent e) {
      connectorTestedHandler.handle(e);
    } else if (event instanceof FlowCreatedEvent e) {
      flowCreatedHandler.handle(e);
    } else if (event instanceof FlowActivatedEvent e) {
      flowActivatedHandler.handle(e);
    } else if (event instanceof FlowExecutedEvent e) {
      flowExecutedHandler.handle(e);
    } else if (event instanceof ExecutionStartedEvent e) {
      executionStartedHandler.handle(e);
    } else if (event instanceof ExecutionCompletedEvent e) {
      executionCompletedHandler.handle(e);
    } else {
      log.warn("未注册的集成领域事件: {}", event.getClass().getName());
    }
  }
}
