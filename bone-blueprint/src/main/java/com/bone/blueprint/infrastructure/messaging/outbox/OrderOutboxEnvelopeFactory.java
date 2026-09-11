package com.bone.blueprint.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Outbox 信封构造：集成事件 → JSON 信封 + 事件ID。
 *
 * <p>入参为 {@code Object}：信封只负责序列化，不绑定具体事件类型，新增集成事件无需改动本类。
 */
@Component
public class OrderOutboxEnvelopeFactory {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public String newEventId() {
    return UUID.randomUUID().toString();
  }

  /**
   * 序列化信封：{@code {"eventId":..., "payload":{...}}}。
   *
   * <p><b>为何信封必须带 eventId</b>：Outbox 是<strong>至少一次</strong>投递（MQ 已收到但标记 SENT 前宕机会重投）， P-5.4 /
   * E-9.6.3 要求消费端<strong>按 eventId 去重</strong>。若只序列化事件本身，消费端拿不到去重键， 「可靠投递」就只做了一半——重复投递会直接变成重复业务动作。
   */
  public String toJson(String eventId, Object event) {
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventId", eventId);
      envelope.put("payload", event);
      return objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Outbox 信封序列化失败", e);
    }
  }
}
