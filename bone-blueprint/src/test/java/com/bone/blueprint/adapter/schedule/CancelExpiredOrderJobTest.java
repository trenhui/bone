package com.bone.blueprint.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.CancelOrderCommand;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.order.projection.OrderHeadProjection;
import com.bone.blueprint.domain.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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

/**
 * 取消超时订单定时任务测试。
 *
 * <p>重点锁住两点：
 *
 * <p>① E-2 的<strong>异步租户传递</strong>——定时线程没有请求上下文，若命令不带租户，应用服务只能取到降级后的平台租户
 * 0，除平台租户外的超时订单将永远不被取消，而日志仍显示「命中 0 笔，成功 0，失败 0」：这类缺陷不会报错，只会静默失效。
 *
 * <p>② <strong>扫描门限确实来自配置</strong>——阈值改为 {@code @Value} 注入后，「读配置」与「读到默认值」在单测里都 可能看起来正常，故显式覆盖为 45
 * 分钟并断言门限随之位移；否则配置项接错线（读了别的键）不会被发现。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelExpiredOrderJobTest {

  @Mock private OrderRepository orderRepository;

  @Mock private OrderApplicationService orderApplicationService;

  private CancelExpiredOrderJob job;

  @BeforeEach
  void setUp() {
    job = new CancelExpiredOrderJob(orderRepository, orderApplicationService);
    // @Value 字段由 Spring 在运行期注入，单测里手工置入（默认 30，与 @Value 占位符默认值一致）
    ReflectionTestUtils.setField(job, "orderTimeoutMinutes", 30L);
  }

  @Test
  void cancelsEachExpiredOrderCarryingItsOwnTenant() {
    when(orderRepository.findCreatedExpiredBeforeAllTenants(any()))
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
    when(orderRepository.findCreatedExpiredBeforeAllTenants(any()))
        .thenReturn(List.of(headRow(0L, 1L), headRow(0L, 2L)));
    doThrow(BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_CONFLICT, "CANCELLED"))
        .when(orderApplicationService)
        .cancel(any());

    // 单笔失败（如已被用户取消）不应中断整批扫描
    job.cancelExpiredOrders();

    verify(orderApplicationService, times(2)).cancel(any());
  }

  @Test
  void scansWithDefaultThirtyMinuteThreshold() {
    when(orderRepository.findCreatedExpiredBeforeAllTenants(any())).thenReturn(List.of());

    job.cancelExpiredOrders();

    Instant before = capturedScanBoundary();
    assertTrue(before.isBefore(Instant.now().minusSeconds(29 * 60)));
    assertTrue(before.isAfter(Instant.now().minusSeconds(31 * 60)));
  }

  @Test
  void scansWithConfiguredThresholdWhenOverridden() {
    // 门限必须随配置位移：接错配置键时会命中默认 30，本断言即失败
    ReflectionTestUtils.setField(job, "orderTimeoutMinutes", 45L);
    when(orderRepository.findCreatedExpiredBeforeAllTenants(any())).thenReturn(List.of());

    job.cancelExpiredOrders();

    Instant before = capturedScanBoundary();
    assertTrue(before.isBefore(Instant.now().minusSeconds(44 * 60)));
    assertTrue(before.isAfter(Instant.now().minusSeconds(46 * 60)));
  }

  private Instant capturedScanBoundary() {
    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(orderRepository).findCreatedExpiredBeforeAllTenants(captor.capture());
    return captor.getValue();
  }

  private static OrderHeadProjection headRow(Long tenantId, Long orderId) {
    return new OrderHeadProjection(
        tenantId, orderId, 3L, new BigDecimal("10.00"), "CREATED", LocalDateTime.now());
  }
}
