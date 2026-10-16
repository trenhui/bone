package com.bone.ainotification.domain.model;

/** 外发结果（投递成功/失败 + 外部引用/错误）。 */
public record NotificationResult(
    NotificationChannel channel, boolean delivered, String externalRef, String error) {}
