package com.bone.blueprint.adapter.mq.listener;

import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 Outbox 中继发出的 {@link OrderPaidIntegrationEvent}（下游系统订阅示范）。
 *
 * <p>注意：此处仅记录消费结果，**不应再次发起支付/确认订单**（避免重复支付）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bone.blueprint.outbox.mq-enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${bone.blueprint.outbox.order-paid-topic}",
    consumerGroup = "${bone.blueprint.outbox.consumer-group:bone-blueprint-order-paid-consumer}")
public class OrderPaidIntegrationMqListener implements RocketMQListener<String> {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Override
  public void onMessage(String envelopeJson) {
    try {
      OrderPaidIntegrationEvent event =
          objectMapper.readValue(envelopeJson, OrderPaidIntegrationEvent.class);
      log.info(
          "消费 OrderPaidIntegrationEvent: orderId={}, tenantId={}, schema={}",
          event.orderId(),
          event.tenantId(),
          event.schemaVersion());
    } catch (Exception ex) {
      log.error("解析 OrderPaidIntegrationEvent 失败: {}", envelopeJson, ex);
      throw new IllegalStateException("invalid integration envelope", ex);
    }
  }
}
