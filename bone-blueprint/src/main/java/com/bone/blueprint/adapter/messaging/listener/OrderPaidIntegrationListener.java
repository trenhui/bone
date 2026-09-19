package com.bone.blueprint.adapter.messaging.listener;

import com.bone.blueprint.application.integration.consumer.OrderPaidIntegrationEventConsumer;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 Outbox 中继发出的 {@link OrderPaidIntegrationEvent}（下游系统订阅示范）。
 *
 * <p><b>本类只做三件事</b>：解析信封 → 校验幂等键存在 → 委派给应用用例（{@code
 * OrderPaidIntegrationEventConsumer}）。幂等落库与业务动作的事务边界在应用层，见该类注释。
 *
 * <p><b>为何必须幂等</b>：Outbox 是至少一次投递——MQ 已收但标记 SENT 前宕机、或中继重试成功两次，都会重复投递。 消费端必须按 {@code eventId}
 * 去重，否则「可靠投递」只是把丢事件换成了重复处理。
 *
 * <p><b>异常不吞</b>：解析失败、缺 eventId、处理失败都抛出，让 MQ 按重试策略重投；只有「已处理过」才正常返回。
 * 吞掉异常会让失败的消息静默消失，而资金相关事实的静默丢失无法事后发现。
 *
 * <p>指标遵循《Bone-消息与事件规范》§9：{@code bone_mq_consume_total{topic,status}}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bone.blueprint.outbox.mq-enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${bone.blueprint.outbox.order-paid-topic}",
    consumerGroup = "${bone.blueprint.outbox.consumer-group:bone-blueprint-order-paid-consumer}")
public class OrderPaidIntegrationListener implements RocketMQListener<String> {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final OrderPaidIntegrationEventConsumer consumptionApplicationService;
  private final MeterRegistry meterRegistry;

  @Value("${bone.blueprint.outbox.order-paid-topic}")
  private String topic;

  @Value("${bone.blueprint.outbox.consumer-group:bone-blueprint-order-paid-consumer}")
  private String consumerGroup;

  @Override
  public void onMessage(String envelopeJson) {
    String eventId;
    OrderPaidIntegrationEvent event;
    try {
      JsonNode envelope = OBJECT_MAPPER.readTree(envelopeJson);
      eventId = envelope.path("eventId").asText(null);
      event = OBJECT_MAPPER.treeToValue(envelope.path("payload"), OrderPaidIntegrationEvent.class);
    } catch (Exception ex) {
      count("parse_failed");
      log.error("解析 OrderPaidIntegrationEvent 失败: {}", envelopeJson, ex);
      throw new IllegalStateException("invalid integration envelope", ex);
    }

    if (eventId == null || eventId.isBlank()) {
      // 信封缺 eventId：无法保证幂等，宁可报错也不冒险重复处理资金相关事件
      count("invalid_envelope");
      log.error("集成事件信封缺少 eventId，拒绝消费（无法幂等）: {}", envelopeJson);
      throw new IllegalStateException("integration envelope without eventId");
    }

    try {
      boolean consumed =
          consumptionApplicationService.consume(eventId, event, consumerGroup, topic);
      count(consumed ? "consumed" : "duplicate");
      if (!consumed) {
        log.info("重复投递已幂等跳过: eventId={}, orderId={}", eventId, event.orderId());
      }
    } catch (RuntimeException ex) {
      // 抛出以触发 MQ 重试：应用用例的事务已整体回滚，幂等记录不会残留，重试可重新处理
      count("failure");
      log.error("消费 OrderPaidIntegrationEvent 失败将重试: eventId={}", eventId, ex);
      throw ex;
    }
  }

  private void count(String status) {
    meterRegistry.counter("bone_mq_consume_total", "topic", topic, "status", status).increment();
  }
}
