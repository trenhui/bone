package com.bone.integration.application.event.port;

/** Outbox 中继：将信封投递至 MQ（或开发态日志通道）。 */
public interface IntegrationMessageSender {

    void send(String topic, String partitionKey, String envelopeJson);
}
