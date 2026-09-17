package com.bone.blueprint.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.event.integration.OrderPaidIntegrationEvent;
import com.bone.blueprint.application.port.out.ConsumedEventPort;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 幂等消费应用用例测试（《Bone-消息与事件规范》§5）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPaidConsumptionApplicationServiceTest {

  private static final String CONSUMER_GROUP = "bone-blueprint-order-paid-consumer";

  private static final String TOPIC = "domain.order.order_paid.v1";

  @Mock private ConsumedEventPort consumedEventPort;

  @InjectMocks private OrderPaidConsumptionApplicationService service;

  @Test
  void consumeWhenFirstTimeClaimsAndProcesses() {
    when(consumedEventPort.tryClaim(any(), any(), any(), any(Long.class))).thenReturn(true);

    boolean consumed = service.consume("e-1", event(7L), CONSUMER_GROUP, TOPIC);

    assertTrue(consumed);
    verify(consumedEventPort).tryClaim(CONSUMER_GROUP, TOPIC, "e-1", 7L);
  }

  @Test
  void consumeWhenDuplicateSkipsProcessing() {
    when(consumedEventPort.tryClaim(any(), any(), any(), any(Long.class))).thenReturn(false);

    boolean consumed = service.consume("e-2", event(7L), CONSUMER_GROUP, TOPIC);

    assertFalse(consumed);
    verify(consumedEventPort).tryClaim(CONSUMER_GROUP, TOPIC, "e-2", 7L);
  }

  @Test
  void consumeFallsBackToPlatformTenantWhenEventCarriesNone() {
    when(consumedEventPort.tryClaim(any(), any(), any(), any(Long.class))).thenReturn(true);

    service.consume("e-3", event(null), CONSUMER_GROUP, TOPIC);

    // 事件缺租户时落平台租户 0，而不是 null 导致插入失败
    verify(consumedEventPort).tryClaim(CONSUMER_GROUP, TOPIC, "e-3", 0L);
  }

  private static OrderPaidIntegrationEvent event(Long tenantId) {
    return OrderPaidIntegrationEvent.fromDomain(
        1L, tenantId, 3L, new BigDecimal("100.00"), Instant.now());
  }
}
