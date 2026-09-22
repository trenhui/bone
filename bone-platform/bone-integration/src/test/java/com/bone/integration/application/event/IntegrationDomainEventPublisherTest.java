package com.bone.integration.application.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.bone.integration.application.event.outbox.IntegrationOutboxWriter;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationDomainEventPublisherTest {

  @Mock private IntegrationOutboxWriter outboxWriter;

  @Mock private ConnectorCreatedHandler connectorCreatedHandler;

  @Mock private ConnectorTestedHandler connectorTestedHandler;

  @Mock private FlowCreatedHandler flowCreatedHandler;

  @Mock private FlowActivatedHandler flowActivatedHandler;

  @Mock private FlowExecutedHandler flowExecutedHandler;

  @Mock private ExecutionStartedHandler executionStartedHandler;

  @Mock private ExecutionCompletedHandler executionCompletedHandler;

  @InjectMocks private IntegrationDomainEventPublisher publisher;

  @Test
  void publishFrom_routesConnectorCreatedEvent() {
    Connector connector =
        Connector.create(1L, "c1", ConnectorType.HTTP, Map.of("url", "http://localhost"));

    publisher.publishFrom(connector);

    verify(outboxWriter).append(org.mockito.ArgumentMatchers.any());
    verify(connectorCreatedHandler).handle(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void publish_routesFlowCreatedEvent() {
    FlowCreatedEvent event = new FlowCreatedEvent(IntegrationFlow.create(2L, "flow", "desc"));

    publisher.publish(event);

    verify(outboxWriter).append(event);
    verify(flowCreatedHandler).handle(event);
  }

  @Test
  void publishFrom_keepsEventsAndPropagatesWhenOutboxAppendFails() {
    Connector connector =
        Connector.create(1L, "c1", ConnectorType.HTTP, Map.of("url", "http://localhost"));
    doThrow(new IllegalStateException("outbox unavailable"))
        .when(outboxWriter)
        .append(org.mockito.ArgumentMatchers.any());

    assertThrows(IllegalStateException.class, () -> publisher.publishFrom(connector));

    assertFalse(connector.getDomainEvents().isEmpty(), "耐久交接失败时不能清除聚合事件");
  }
}
