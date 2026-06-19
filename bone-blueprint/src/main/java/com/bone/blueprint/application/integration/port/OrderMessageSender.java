package com.bone.blueprint.application.integration.port;

/** Outbox 中继出站端口：将信封 JSON 投递至 MQ（或开发态日志通道）。 */
public interface OrderMessageSender {

  void send(String topic, String partitionKey, String envelopeJson);
}
