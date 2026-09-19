package com.bone.blueprint.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderOutboxPort;
import com.bone.blueprint.application.query.port.PaymentQueryPort;
import com.bone.blueprint.application.query.projection.PaymentProjection;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Mock private OrderRepository orderRepository;

  @Mock private OrderOutboxPort orderOutboxWriter;

  private OrderPaymentInconsistencyJob job;

  @BeforeEach
  void setUp() {
    job = new OrderPaymentInconsistencyJob(paymentQueryPort, orderRepository, orderOutboxWriter);
    // @Value 字段由 Spring 在运行期注入，单测里手工置入（默认 10，与 @Value 占位符默认值一致）
    ReflectionTestUtils.setField(job, "confirmGraceMinutes", 10L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void looksUpOrderStatusByIdPerTenantWithoutJoin() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(777L, 11L, 1L)));
    when(orderRepository.findStatusById(777L, 1L))
        .thenReturn(Optional.of(com.bone.blueprint.domain.order.valueobject.OrderStatus.PAID));

    job.checkPaidButOrderNotConfirmed();

    verify(orderRepository).findStatusById(777L, 1L);
  }

  @Test
  void treatsMissingOrderAsInconsistent() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(0L, 11L, 2L)));
    when(orderRepository.findStatusById(0L, 2L)).thenReturn(Optional.empty());

    // 支付成功却没有订单同样是异常，必须进入告警（可观测性由 error 日志 + 落 Outbox 共同承担）
    job.checkPaidButOrderNotConfirmed();

    verify(orderRepository).findStatusById(0L, 2L);
    // 安全网检出的偏差须同事务落 Outbox，接入统一告警/工单链路（可持久观测，而非仅沉没日志）
    verify(orderOutboxWriter).appendPaymentInconsistent(any());
  }

  /**
   * 定时线程没有请求上下文，Outbox 落库走 SDK 写路径（save 内部先按主键+租户探测），必须逐行以「该行的租户」执行， 否则偏差事件被 ADR-0029
   * 失败关闭拦下、安全网静默失效。
   */
  @Test
  void appendsInconsistentEventInsideRowTenantContext() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(777L, 11L, 1L)));
    when(orderRepository.findStatusById(777L, 1L)).thenReturn(Optional.empty());
    AtomicReference<String> tenantDuringAppend = new AtomicReference<>();
    doAnswer(
            invocation -> {
              tenantDuringAppend.set(TenantContext.getTenantId());
              return null;
            })
        .when(orderOutboxWriter)
        .appendPaymentInconsistent(any());

    job.checkPaidButOrderNotConfirmed();

    assertEquals("777", tenantDuringAppend.get());
    assertNull(TenantContext.getTenantId());
  }

  /** 落库失败（异常穿出扫描方法）时也必须恢复上下文，否则定时线程会带着残留租户继续下一行。 */
  @Test
  void contextIsRestoredWhenAppendFails() {
    when(paymentQueryPort.findSuccessCreatedBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(777L, 11L, 1L)));
    when(orderRepository.findStatusById(777L, 1L)).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("db down"))
        .when(orderOutboxWriter)
        .appendPaymentInconsistent(any());

    assertThrows(IllegalStateException.class, job::checkPaidButOrderNotConfirmed);

    assertNull(TenantContext.getTenantId());
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
