package com.bone.integration.application.event.outbox;

import com.bone.core.domain.DomainEvent;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import com.bone.integration.domain.model.connector.event.ConnectorTestedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import com.bone.integration.domain.model.flow.event.FlowExecutedEvent;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 集成领域事件 Topic 登记（对齐 {@code Bone-消息与事件规范.md} §4）。 */
@Component
public class IntegrationEventCatalog {

  private static final Map<Class<? extends DomainEvent>, IntegrationEventRegistration> REGISTRY =
      Map.ofEntries(
          Map.entry(
              ConnectorCreatedEvent.class,
              reg("ConnectorCreated", "domain.integration.connector_created.v1")),
          Map.entry(
              ConnectorTestedEvent.class,
              reg("ConnectorTested", "domain.integration.connector_tested.v1")),
          Map.entry(
              FlowCreatedEvent.class, reg("FlowCreated", "domain.integration.flow_created.v1")),
          Map.entry(
              FlowActivatedEvent.class,
              reg("FlowActivated", "domain.integration.flow_activated.v1")),
          Map.entry(
              FlowExecutedEvent.class, reg("FlowExecuted", "domain.integration.flow_executed.v1")),
          Map.entry(
              ExecutionStartedEvent.class,
              reg("ExecutionStarted", "domain.integration.execution_started.v1")),
          Map.entry(
              ExecutionCompletedEvent.class,
              reg("ExecutionCompleted", "domain.integration.execution_completed.v1")));

  public Optional<IntegrationEventRegistration> resolve(DomainEvent event) {
    if (event == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(REGISTRY.get(event.getClass()));
  }

  private static IntegrationEventRegistration reg(String eventType, String topic) {
    return new IntegrationEventRegistration(eventType, topic);
  }
}
