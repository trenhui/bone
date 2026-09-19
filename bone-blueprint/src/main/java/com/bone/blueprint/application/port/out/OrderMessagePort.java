package com.bone.blueprint.application.port.out;

/**
 * 消息投递出站端口（技术出站端口，E-4.3 / E-10.2）：将 Outbox 信封 JSON 投递至 MQ（或开发态 日志通道）。MQ 投递属应用流程需要的
 * <strong>技术能力</strong>，声明于 {@code application/port/out}，实现于 infrastructure；不进 {@code
 * domain/gateway}。 实现见 {@code infrastructure/messaging/LoggingOrderMessagePortAdapter} / {@code
 * RocketMqOrderMessagePortAdapter}。
 */
public interface OrderMessagePort {

  void send(String topic, String partitionKey, String envelopeJson);
}
