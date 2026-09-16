package com.bone.blueprint.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.query.dto.OrderHeadRow;
import com.bone.blueprint.application.query.port.OrderReadPort;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 取消超时订单定时任务测试。
 *
 * <p>重点锁住 E-4.4 的<strong>异步租户传递</strong>：定时线程没有请求上下文，若命令不带租户，Handler 只能取到降级后的 平台租户
 * 0，除平台租户外的超时订单将永远不被取消，而日志仍显示「扫描完成」——这类缺陷不会报错，只会静默失效。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelExpiredOrderJobTest {

  @Mock private OrderReadPort orderReadPort;

  @Mock private OrderApplicationService orderApplicationService;

  @InjectMocks private CancelExpiredOrderJob job;

  @Test
  void cancelsEachExpiredOrderCarryingItsOwnTenant() {
    when(orderReadPort.findCreatedExpiredBeforeAllTenants(any()))
        .thenReturn(List.of(headRow(0L, 1L), headRow(999L, 2L)));

    job.cancelExpiredOrders();

    ArgumentCaptor<CancelOrderCommand> captor = ArgumentCaptor.forClass(CancelOrderCommand.class);
    verify(orderApplicationService, times(2)).cancel(captor.capture());
    assertEquals(
        List.of(0L, 999L),
        captor.getAllValues().stream().map(CancelOrderCommand::tenantId).toList(),
        "命令必须显式携带该行自己的租户，不能依赖线程上下文");
  }

  @Test
  void continuesWithRemainingRowsWhenOneFails() {
    when(orderReadPort.findCreatedExpiredBeforeAllTenants(any()))
        .thenReturn(List.of(headRow(0L, 1L), headRow(0L, 2L)));
    doThrow(new BizException(409, "BP_ORDER_STATUS_CONFLICT: CANCELLED"))
        .when(orderApplicationService)
        .cancel(any());

    // 单笔失败（如已被用户取消）不应中断整批扫描
    job.cancelExpiredOrders();

    verify(orderApplicationService, times(2)).cancel(any());
  }

  @Test
  void scansWithThirtyMinuteThreshold() {
    when(orderReadPort.findCreatedExpiredBeforeAllTenants(any())).thenReturn(List.of());

    job.cancelExpiredOrders();

    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(orderReadPort).findCreatedExpiredBeforeAllTenants(captor.capture());
    Instant before = captor.getValue();
    assertTrue(before.isBefore(Instant.now().minusSeconds(29 * 60)));
    assertTrue(before.isAfter(Instant.now().minusSeconds(31 * 60)));
  }

  private static OrderHeadRow headRow(Long tenantId, Long orderId) {
    return new OrderHeadRow(
        tenantId, orderId, 3L, new BigDecimal("10.00"), "CREATED", LocalDateTime.now());
  }
}
