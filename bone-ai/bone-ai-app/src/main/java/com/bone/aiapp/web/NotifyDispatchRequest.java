package com.bone.aiapp.web;

import com.bone.ainotification.domain.model.NotificationChannel;

/**
 * 消警外发请求（API 层 DTO）。
 *
 * <p>不含 tenantId——租户一律从上下文取（详设 §3.1），禁入参传租户。
 */
public record NotifyDispatchRequest(
    String alertId,
    String severity,
    String title,
    String body,
    String sourceModule,
    NotificationChannel channel,
    String target) {}
