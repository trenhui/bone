package com.bone.ainotification.domain.model;

import java.time.Instant;

/** 预警确认（ack）回写载荷——消警闭环末端。 */
public record NotificationAck(
    String alertId, Long tenantId, String ackedBy, Instant ackedAt, String note) {}
