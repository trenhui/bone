package com.bone.integration.application.event.outbox;

import java.time.Instant;
import java.util.Map;

/** 消息规范 §3 信封（JSON 序列化字段名与规范一致）。 */
public record IntegrationEventEnvelope(
    String eventId,
    String eventType,
    String topic,
    Instant occurredAt,
    String tenantId,
    String traceId,
    String schemaVersion,
    Map<String, Object> payload) {}
