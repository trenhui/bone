package com.bone.integration.application.event.outbox;

import com.bone.core.domain.DomainEvent;
import com.bone.core.tenant.context.TenantContext;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import com.bone.integration.domain.model.connector.event.ConnectorTestedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import com.bone.integration.domain.model.flow.event.FlowExecutedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class IntegrationEventEnvelopeFactory {

  private static final String SCHEMA_VERSION = "1.0";

  private final ObjectMapper objectMapper;

  public IntegrationEventEnvelopeFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public IntegrationEventEnvelope create(
      DomainEvent event, IntegrationEventRegistration registration) {
    String eventId = UUID.randomUUID().toString();
    String tenantId = String.valueOf(tenantIdOrDefault());
    String traceId = resolveTraceId();
    Map<String, Object> payload = toPayload(event);
    return new IntegrationEventEnvelope(
        eventId,
        registration.eventType(),
        registration.topic(),
        Instant.now(),
        tenantId,
        traceId,
        SCHEMA_VERSION,
        payload);
  }

  public String toJson(IntegrationEventEnvelope envelope) {
    try {
      return objectMapper.writeValueAsString(envelope);
    } catch (Exception ex) {
      throw new IllegalStateException("序列化集成事件信封失败", ex);
    }
  }

  private long tenantIdOrDefault() {
    Long tenantId = TenantContext.getTenantId();
    return tenantId != null ? tenantId : 0L;
  }

  private String resolveTraceId() {
    String traceId = MDC.get("traceId");
    if (traceId == null || traceId.isBlank()) {
      traceId = MDC.get("trace_id");
    }
    return (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
  }

  private Map<String, Object> toPayload(DomainEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (event instanceof ConnectorCreatedEvent e) {
      payload.put("connectorId", e.connectorId());
      payload.put("name", e.name());
      payload.put("type", e.type());
    } else if (event instanceof ConnectorTestedEvent e) {
      payload.put("connectorId", e.connectorId());
      payload.put("success", e.success());
      payload.put("message", e.message());
    } else if (event instanceof FlowCreatedEvent e) {
      payload.put("flowId", e.flowId());
      payload.put("name", e.name());
      payload.put("description", e.description());
    } else if (event instanceof FlowActivatedEvent e) {
      payload.put("flowId", e.flowId());
      payload.put("name", e.name());
    } else if (event instanceof FlowExecutedEvent e) {
      payload.put("flowId", e.flowId());
      payload.put("executionId", e.executionId());
      payload.put("success", e.success());
      payload.put("message", e.message());
    } else if (event instanceof ExecutionStartedEvent e) {
      payload.put("executionId", e.executionId());
      payload.put("flowId", e.flowId());
      payload.put("inputData", e.inputData());
    } else if (event instanceof ExecutionCompletedEvent e) {
      payload.put("executionId", e.executionId());
      payload.put("flowId", e.flowId());
      payload.put("success", e.success());
      payload.put("outputData", e.outputData());
      payload.put("errorMessage", e.errorMessage());
    } else {
      payload.putAll(objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() {}));
    }
    return payload;
  }
}
