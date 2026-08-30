package com.bone.blueprint.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

  public String toJson(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Outbox 信封序列化失败", e);
    }
  }
}
