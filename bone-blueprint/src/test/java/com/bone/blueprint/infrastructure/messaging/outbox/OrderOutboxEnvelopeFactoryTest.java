package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.bone.blueprint.domain.integration.event.OrderPaidIntegrationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Outbox 信封契约测试：必须满足《Bone-消息与事件规范》§3 的必填字段。
 *
 * <p><b>回归防护</b>：Outbox 是至少一次投递——缺 {@code eventId} 则「消费端幂等」在物理上不可能满足；缺 {@code topic} 则死信重放要回查
 * Outbox 表；缺 {@code traceId} 则消息与产生它的那次调用断链，资金问题无法回溯；{@code occurredAt} 若退化成投递时间，下游按它排序/对账就会错位。
 */
class OrderOutboxEnvelopeFactoryTest {

  private static final String TRACE_ID = "9f2c1b7a4e6d4b1f";

  private static final String TOPIC = "domain.order.order_paid.v1";

  private final OrderOutboxEnvelopeFactory factory = new OrderOutboxEnvelopeFactory();

  @BeforeEach
  void setUp() {
    MDC.put("traceId", TRACE_ID);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void testEnvelopeCarriesAllRequiredFields() throws Exception {
    Instant occurredAt = Instant.parse("2026-09-16T02:00:00Z");
    OrderPaidIntegrationEvent event =
        OrderPaidIntegrationEvent.fromDomain(1L, 2L, 3L, new BigDecimal("200"), occurredAt);

    String json = factory.toJson("e-1", "OrderPaidIntegrationEvent", TOPIC, 2L, occurredAt, event);

    JsonNode envelope = new ObjectMapper().readTree(json);
    assertEquals("e-1", envelope.path("eventId").asText());
    assertEquals("OrderPaidIntegrationEvent", envelope.path("eventType").asText());
    assertEquals(TOPIC, envelope.path("topic").asText());
    assertEquals("2", envelope.path("tenantId").asText());
    assertEquals(TRACE_ID, envelope.path("traceId").asText());
    assertEquals("1.0", envelope.path("schemaVersion").asText());
    // 时间必须是 ISO-8601 字符串（不能是 Jackson 默认的 epoch 数值）
    assertEquals("2026-09-16T02:00:00Z", envelope.path("occurredAt").asText());
    // 载荷保持原样，且下游仍能读到业务字段
    assertEquals(1L, envelope.path("payload").path("orderId").asLong());
    assertEquals("1.0", envelope.path("payload").path("schemaVersion").asText());
  }

  @Test
  void testTraceIdFallsBackOutsideRequestContext() throws Exception {
    MDC.clear();

    String json =
        factory.toJson("e-2", "OrderPaidIntegrationEvent", TOPIC, 0L, Instant.now(), "payload");

    JsonNode envelope = new ObjectMapper().readTree(json);
    assertFalse(
        envelope.path("traceId").asText().isBlank(), "无请求上下文（如定时中继）时也必须给出 traceId，否则消息无法与日志关联");
  }
}
