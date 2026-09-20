package com.bone.blueprint.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.PaymentApplicationService;
import com.bone.blueprint.application.command.CloseExpiredPaymentCommand;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.payment.projection.PaymentProjection;
import com.bone.blueprint.domain.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 关闭超时支付单定时任务测试（同样锁住全租户扫描 + 命令显式携带租户）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloseExpiredPaymentJobTest {

  @Mock private PaymentRepository paymentRepository;

  @Mock private PaymentApplicationService paymentApplicationService;

  private CloseExpiredPaymentJob job;

  @BeforeEach
  void setUp() {
    job = new CloseExpiredPaymentJob(paymentRepository, paymentApplicationService);
    // @Value 字段由 Spring 在运行期注入，单测里手工置入（默认 30，与 @Value 占位符默认值一致）
    ReflectionTestUtils.setField(job, "paymentTimeoutMinutes", 30L);
  }

  @Test
  void closesEachExpiredPaymentCarryingItsOwnTenant() {
    when(paymentRepository.findExpiredOpenPaymentsAllTenants(any()))
        .thenReturn(List.of(paymentRow(0L, 11L, 1L), paymentRow(888L, 12L, 2L)));

    job.closeExpiredPayments();

    ArgumentCaptor<CloseExpiredPaymentCommand> captor =
        ArgumentCaptor.forClass(CloseExpiredPaymentCommand.class);
    verify(paymentApplicationService, times(2)).closeExpired(captor.capture());
    assertEquals(
        List.of(0L, 888L),
        captor.getAllValues().stream().map(CloseExpiredPaymentCommand::tenantId).toList());
  }

  @Test
  void continuesWithRemainingRowsWhenOneFails() {
    when(paymentRepository.findExpiredOpenPaymentsAllTenants(any()))
        .thenReturn(List.of(paymentRow(0L, 11L, 1L), paymentRow(0L, 12L, 2L)));
    doThrow(BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_STATUS_CONFLICT, "SUCCESS"))
        .when(paymentApplicationService)
        .closeExpired(any());

    job.closeExpiredPayments();

    verify(paymentApplicationService, times(2)).closeExpired(any());
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
