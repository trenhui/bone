package com.bone.integration.application.event.outbox;

/** 集成领域事件在消息规范中的登记项。 */
public record IntegrationEventRegistration(String eventType, String topic) {}
