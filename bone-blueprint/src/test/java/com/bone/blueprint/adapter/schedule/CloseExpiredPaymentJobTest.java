package com.bone.blueprint.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.command.cmd.CloseExpiredPaymentCommand;
import com.bone.blueprint.application.command.handler.CloseExpiredPaymentCommandHandler;
import com.bone.blueprint.application.query.dto.PaymentProjection;
import com.bone.blueprint.application.query.port.PaymentQueryPort;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 关闭超时支付单定时任务测试（同样锁住全租户扫描 + 命令显式携带租户）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloseExpiredPaymentJobTest {

  @Mock private PaymentQueryPort paymentQueryPort;

  @Mock private CloseExpiredPaymentCommandHandler closeExpiredPaymentCommandHandler;

  @InjectMocks private CloseExpiredPaymentJob job;

  @Test
  void closesEachExpiredPaymentCarryingItsOwnTenant() {
    when(paymentQueryPort.findPayableExpiredBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(0L, 11L, 1L), paymentRow(888L, 12L, 2L)));

    job.closeExpiredPayments();

    ArgumentCaptor<CloseExpiredPaymentCommand> captor =
        ArgumentCaptor.forClass(CloseExpiredPaymentCommand.class);
    verify(closeExpiredPaymentCommandHandler, times(2)).handle(captor.capture());
    assertEquals(
        List.of(0L, 888L),
        captor.getAllValues().stream().map(CloseExpiredPaymentCommand::tenantId).toList());
  }

  @Test
  void continuesWithRemainingRowsWhenOneFails() {
    when(paymentQueryPort.findPayableExpiredBeforeAllTenants(any()))
        .thenReturn(List.of(paymentRow(0L, 11L, 1L), paymentRow(0L, 12L, 2L)));
    doThrow(new BizException(409, "BP_PAYMENT_STATUS_CONFLICT: SUCCESS"))
        .when(closeExpiredPaymentCommandHandler)
        .handle(any());

    job.closeExpiredPayments();

    verify(closeExpiredPaymentCommandHandler, times(2)).handle(any());
  }

  private static PaymentProjection paymentRow(Long tenantId, Long paymentId, Long orderId) {
    return new PaymentProjection(
        tenantId,
        paymentId,
        orderId,
        3L,
        new BigDecimal("10.00"),
        "SIMULATED",
        "PENDING",
        null,
        null,
        null,
        null,
        null,
        Instant.now());
  }
}
