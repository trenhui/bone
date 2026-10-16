package com.bone.ainotification.domain.model;

/** 外发通道（第三批扩展点；具体投递由基础设施适配）。 */
public enum NotificationChannel {
  IM,
  EMAIL,
  WEBHOOK,
  SMS
}
