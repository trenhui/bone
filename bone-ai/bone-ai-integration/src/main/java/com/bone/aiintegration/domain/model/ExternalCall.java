package com.bone.aiintegration.domain.model;

/** 外部系统调用请求（integration AI 节点的出站载荷）。 */
public record ExternalCall(
    String callId,
    Long tenantId,
    String systemId,
    String endpoint,
    String payload,
    IntegrationMode mode,
    String correlationId) {}
