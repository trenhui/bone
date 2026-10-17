package com.bone.aiapp.web;

import com.bone.aiintegration.domain.model.IntegrationMode;

/** 集成调用请求（API 层 DTO）。 */
public record IntegrationInvokeRequest(
    String callId,
    Long tenantId,
    String systemId,
    String endpoint,
    String payload,
    IntegrationMode mode,
    String correlationId) {}
