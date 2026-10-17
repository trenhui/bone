package com.bone.aiapp.web;

import com.bone.aiintegration.domain.model.IntegrationMode;

/**
 * 集成调用请求（API 层 DTO，与领域 ExternalCall 解耦）。
 *
 * <p>不含 tenantId——租户一律从上下文取（详设 §3.1），禁入参传租户。
 */
public record IntegrationInvokeRequest(
    String callId,
    String systemId,
    String endpoint,
    String payload,
    IntegrationMode mode,
    String correlationId) {}
