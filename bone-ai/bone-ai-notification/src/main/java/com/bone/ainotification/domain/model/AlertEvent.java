package com.bone.ainotification.domain.model;

import java.time.Instant;

/** 预警事件（消警闭环的触发源）。由编排/规则/阈值等上游产生，经本模块外发并等待 ack。 */
public record AlertEvent(
    String alertId,
    Long tenantId,
    String severity,
    String title,
    String body,
    String sourceModule,
    Instant occurredAt) {}
