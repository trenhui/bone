package com.bone.blueprint.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Outbox 信封构造：集成事件 → JSON 信封 + 事件ID。
 *
 * <p>入参为 {@code Object}：信封只负责序列化，不绑定具体事件类型，新增集成事件无需改动本类。
 */
@Component
public class OrderOutboxEnvelopeFactory {

  /** 信封 schema 版本（消息与事件规范 §3 必填）。 */
  private static final String ENVELOPE_SCHEMA_VERSION = "1.0";

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          // 时间用 ISO-8601 字符串而非 epoch 数值（API 规范 §14.1 / 消息规范 §3）
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public String newEventId() {
    return UUID.randomUUID().toString();
  }

  /**
   * 序列化信封（字段对齐《Bone-消息与事件规范》§3）。
   *
   * <p><b>为何信封必须带 eventId</b>：Outbox 是<strong>至少一次</strong>投递（MQ 已收到但标记 SENT 前宕机会重投），
   * 要求消费端<strong>按 eventId 去重</strong>。若只序列化事件本身，消费端拿不到去重键， 「可靠投递」就只做了一半——重复投递会直接变成重复业务动作。
   *
   * <p><b>为何补齐其余必填字段</b>：{@code eventType} 用于消费端路由与注册表核对；{@code topic} 让 dlq 重放时无需回查 Outbox
   * 表；{@code occurredAt} 是<strong>事实发生时间</strong>（不是投递时间），下游按它排序/对账；{@code traceId} 与 HTTP 请求/MDC
   * 打通，否则一条消息出事后无法回溯是哪次调用产生的；{@code schemaVersion} 支撑载荷演进。
   *
   * <p>时间统一 ISO-8601 UTC（关闭 Jackson 默认的时间戳数值形式），与 API 规范 §14.1 一致。
   */
  public String toJson(
      String eventId,
      String eventType,
      String topic,
      long tenantId,
      Instant occurredAt,
      Object payload) {
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventId", eventId);
      envelope.put("eventType", eventType);
      envelope.put("topic", topic);
      envelope.put("occurredAt", occurredAt);
      envelope.put("tenantId", String.valueOf(tenantId));
      envelope.put("traceId", resolveTraceId());
      envelope.put("schemaVersion", ENVELOPE_SCHEMA_VERSION);
      envelope.put("payload", payload);
      return objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Outbox 信封序列化失败", e);
    }
  }

  /**
   * 取当前请求的 traceId；定时中继等无请求上下文的场景回退为新生成值。
   *
   * <p>信封在<strong>业务事务内</strong>构造，此时 MDC 已由 {@code BoneRequestContextFilter} 写入 traceId，因此消息可
   * 与产生它的那次 HTTP 调用、日志、{@code ProblemDetail.traceId} 一一对应。
   */
  private String resolveTraceId() {
    String traceId = MDC.get("traceId");
    return traceId != null && !traceId.isBlank() ? traceId : newEventId().replace("-", "");
  }
}
