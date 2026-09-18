package com.bone.blueprint.adapter.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderOutboxWriter;
import com.bone.blueprint.application.query.dto.PaymentProjection;
import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.application.query.port.PaymentQueryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 「钱货不一致」对账任务测试。
 *
 * <p>锁住两点：① 全租户扫描（否则除平台租户外的不一致永远发现不了）；② 跨上下文按 ID 查询订单状态而不是 SQL JOIN
 * （支付与订单是两个上下文，联表会把两个上下文的数据所有权耦合在一起）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPaymentInconsistencyJobTest {

  @Mock private PaymentQueryPort paymentQueryPort;

  @Mock private OrderQueryPort orderQueryPort;

  @Mock private OrderOutboxWriter orderOutboxWriter;

  @InjectMocks private OrderPaymentInconsistencyJob job;

  @Test
  void looksUpOrderStatusByIdPerTenantWithoutJoin() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(777L, 11L, 1L)));
    when(orderQueryPort.findStatusById(777L, 1L))
        .thenReturn(Optional.of(com.bone.blueprint.domain.order.valueobject.OrderStatus.PAID));

    job.checkPaidButOrderNotConfirmed();

    verify(orderQueryPort).findStatusById(777L, 1L);
  }

  @Test
  void treatsMissingOrderAsInconsistent() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(0L, 11L, 2L)));
    when(orderQueryPort.findStatusById(0L, 2L)).thenReturn(Optional.empty());

    // 支付成功却没有订单同样是异常，必须进入告警（可观测性由 error 日志 + 落 Outbox 共同承担）
    job.checkPaidButOrderNotConfirmed();

    verify(orderQueryPort).findStatusById(0L, 2L);
    // 安全网检出的偏差须同事务落 Outbox，接入统一告警/工单链路（可持久观测，而非仅沉没日志）
    verify(orderOutboxWriter).appendPaymentInconsistent(any());
  }

  @Test
  void handlesEmptyScanResult() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any())).thenReturn(List.of());

    job.checkPaidButOrderNotConfirmed();

    verify(paymentQueryPort).findSuccessCreatedBeforeAllTenants(any());
  }

  private static PaymentProjection paymentRow(Long tenantId, Long paymentId, Long orderId) {
    return new PaymentProjection(
        tenantId,
        paymentId,
        orderId,
        3L,
        new BigDecimal("10.00"),
        "SIMULATED",
        "SUCCESS",
        "CH-1",
        null,
        Instant.now(),
        null,
        null,
        Instant.now());
  }
}
