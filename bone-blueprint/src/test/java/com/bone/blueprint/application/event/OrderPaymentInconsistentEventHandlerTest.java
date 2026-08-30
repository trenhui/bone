package com.bone.blueprint.application.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.bone.blueprint.application.event.outbox.OrderOutboxWriter;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 「钱货不一致」处理器测试。
 *
 * <p><b>回归防护</b>：异常必须落 Outbox，而非直接调告警通道——后者在外部系统超时/不可用时会丢失异常， 等于没有补偿。走 Outbox
 * 由中继保证至少一次投递，与订单支付事件复用同一套可靠机制。
 */
@ExtendWith(MockitoExtension.class)
class OrderPaymentInconsistentEventHandlerTest {

  @Mock private OrderOutboxWriter orderOutboxWriter;

  @InjectMocks private OrderPaymentInconsistentEventHandler handler;

  @Test
  void testAppendsOutboxCompensationEvent() {
    OrderPaymentInconsistentEvent event =
        new OrderPaymentInconsistentEvent(1L, 100L, 2L, "CANCELLED", "订单已取消却收到收款", Instant.now());

    handler.handle(event);

    verify(orderOutboxWriter).appendPaymentInconsistent(any());
  }
}
