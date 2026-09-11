package com.bone.blueprint.application.query.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.application.query.qry.PaymentDetailQuery;
import com.bone.blueprint.domain.gateway.PaymentReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.read.PaymentRow;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentDetailQueryHandlerTest {

  @Mock private PaymentReadPort paymentReadPort;
  @Mock private TenantProvider tenantProvider;

  @InjectMocks private PaymentDetailQueryHandler handler;

  private PaymentRow row() {
    return new PaymentRow(
        1L,
        1L,
        100L,
        200L,
        new BigDecimal("200"),
        "SIMULATED",
        "SUCCESS",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void testHandleReturnsDto() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentReadPort.findById(1L, 1L)).thenReturn(Optional.of(row()));

    PaymentDto dto = handler.handle(new PaymentDetailQuery(1L));

    assertEquals(1L, dto.getPaymentId());
    assertEquals(100L, dto.getOrderId());
    assertEquals(new BigDecimal("200"), dto.getAmount());
    assertEquals("SUCCESS", dto.getStatus());
  }

  @Test
  void testHandleNotFound() {
    when(tenantProvider.currentTenantId()).thenReturn(1L);
    when(paymentReadPort.findById(1L, 1L)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> handler.handle(new PaymentDetailQuery(1L)));
  }
}
