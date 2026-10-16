package com.bone.aiintegration.domain.model;

/** 外部系统原始调用结果（传输层产出，三态语义由 IntegrationPolicy 裁定）。 */
public record ExternalCallOutcome(
    String callId, String systemId, int httpStatus, String body, long latencyMs, String error) {}
