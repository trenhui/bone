package com.bone.integration.application.query.dto;

public record FlowStatisticsDto(
    Long flowId,
    String flowName,
    long executionCount,
    long successCount,
    long failureCount,
    double successRate) {}
