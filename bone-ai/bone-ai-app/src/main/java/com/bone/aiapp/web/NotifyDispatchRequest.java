package com.bone.aiapp.web;

import com.bone.ainotification.domain.model.NotificationChannel;

/** 消警外发请求（API 层 DTO）。 */
public record NotifyDispatchRequest(
    String alertId,
    Long tenantId,
    String severity,
    String title,
    String body,
    String sourceModule,
    NotificationChannel channel,
    String target) {}
