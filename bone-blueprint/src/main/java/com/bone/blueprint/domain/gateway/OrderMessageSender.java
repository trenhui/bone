package com.bone.blueprint.domain.gateway;

/**
 * 消息投递出站端口（E-10 出站端口，声明于 domain，实现于基础设施）：将 Outbox 信封 JSON 投递至 MQ（或开发态 日志通道）。实现见 {@code
 * infrastructure/messaging/LoggingOrderMessageSender} / {@code RocketMqOrderMessageSender}。
 */
public interface OrderMessageSender {

  void send(String topic, String partitionKey, String envelopeJson);
}
