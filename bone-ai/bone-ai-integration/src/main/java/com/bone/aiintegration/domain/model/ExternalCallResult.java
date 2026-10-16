package com.bone.aiintegration.domain.model;

/** 集成调用结果（含三态语义：used=已用于流程 / shadow=仅观测不落地）。 */
public record ExternalCallResult(
    String callId,
    String systemId,
    int httpStatus,
    String body,
    IntegrationMode mode,
    boolean used,
    boolean shadow,
    long latencyMs,
    String error) {}
