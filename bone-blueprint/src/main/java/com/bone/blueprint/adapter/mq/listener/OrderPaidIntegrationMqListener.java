package com.bone.blueprint.adapter.mq.listener;

import com.bone.blueprint.domain.integration.event.OrderPaidIntegrationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 Outbox 中继发出的 {@link OrderPaidIntegrationEvent}（下游系统订阅示范）。
 *
 * <p><b>消费端按 eventId 幂等（P-5.4 / E-9.6.3）</b>：Outbox 是<strong>至少一次</strong>投递——MQ 收到但标记 SENT
 * 前宕机、或中继重试成功两次，都会导致重复投递。消费端必须按 {@code eventId} 去重，否则"可靠投递"只是把丢事件 换成了重复处理。
 *
 * <p><b>本实现的边界（演示用）</b>：去重集合在<strong>进程内存</strong>中，重启即失效，仅用于说明幂等位置与判定方式。
 * 生产必须用<strong>持久化去重表</strong>（{@code event_id} 唯一索引，保留期 ≥ 最长重试窗口），否则重启后仍会重复消费。
 *
 * <p>注意：此处仅记录消费结果，**不应再次发起支付/确认订单**（避免重复业务动作）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bone.blueprint.outbox.mq-enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${bone.blueprint.outbox.order-paid-topic}",
    consumerGroup = "${bone.blueprint.outbox.consumer-group:bone-blueprint-order-paid-consumer}")
public class OrderPaidIntegrationMqListener implements RocketMQListener<String> {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  /** 已处理事件 ID（演示用内存去重；生产替换为持久化去重表）。 */
  private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

  @Override
  public void onMessage(String envelopeJson) {
    String eventId;
    OrderPaidIntegrationEvent event;
    try {
      JsonNode envelope = objectMapper.readTree(envelopeJson);
      eventId = envelope.path("eventId").asText(null);
      event = objectMapper.treeToValue(envelope.path("payload"), OrderPaidIntegrationEvent.class);
    } catch (Exception ex) {
      log.error("解析 OrderPaidIntegrationEvent 失败: {}", envelopeJson, ex);
      throw new IllegalStateException("invalid integration envelope", ex);
    }

    if (eventId == null || eventId.isBlank()) {
      // 信封缺 eventId：无法保证幂等，宁可报错也不冒险重复处理资金相关事件
      log.error("集成事件信封缺少 eventId，拒绝消费（无法幂等）: {}", envelopeJson);
      throw new IllegalStateException("integration envelope without eventId");
    }
    if (!processedEventIds.add(eventId)) {
      log.info("重复投递已幂等跳过: eventId={}, orderId={}", eventId, event.orderId());
      return;
    }

    log.info(
        "消费 OrderPaidIntegrationEvent: eventId={}, orderId={}, tenantId={}, schema={}",
        eventId,
        event.orderId(),
        event.tenantId(),
        event.schemaVersion());
  }
}
