package com.bone.blueprint.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.core.enums.Operator;
import com.bone.core.model.PageResult;
import com.bone.core.model.QueryParam;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 写侧仓储 {@code findByIdInTenant} 的租户隔离契约测试——锁住「失败关闭」这一安全不变量。
 *
 * <p><b>为什么值得单测</b>：domain 层改用 bone-core {@code QueryParam} + {@code Operator} 之后（CORE-05：读侧 DSL
 * 不得进入 domain），条件表达能力换了实现，但 {@code BaseRepository#buildCriteria} 会<strong>静默丢弃值为 {@code null}
 * 的条件</strong>。一旦调用方漏传租户（事件载荷的 {@code tenantId} 是可为空的 {@code Long}），租户条件会整条消失， 查询退化为「按 id
 * 跨租户读取」——这是多租户系统里最难在功能性测试中暴露、后果最严重的一类退化。
 *
 * <p>此处同时验证两件事：① 租户或 id 缺失时<strong>立即返回 null 且不触达查询</strong>（失败关闭）； ② 两者齐全时两条 {@code EQ}
 * 条件都被下发（隔离条件未被优化掉）。
 */
class RepositoryTenantIsolationTest {

  private static final long ORDER_ID = 100L;
  private static final long TENANT_ID = 1L;

  private final OrderRepository orderRepository =
      mock(OrderRepository.class, Mockito.CALLS_REAL_METHODS);

  private final PaymentRepository paymentRepository =
      mock(PaymentRepository.class, Mockito.CALLS_REAL_METHODS);

  @Test
  void orderLookupFailsClosedWithoutTenant() {
    assertNull(orderRepository.findByIdInTenant(ORDER_ID, null));
    assertNull(orderRepository.findByIdInTenant(null, TENANT_ID));
    verify(orderRepository, never()).queryByCondition(anyList(), any(), any(), any(), any());
  }

  @Test
  void orderLookupAppliesIdAndTenantConditions() {
    Order order = sampleOrder();
    doReturn(PageResult.of(List.of(order), 1L, 1, 1))
        .when(orderRepository)
        .queryByCondition(anyList(), any(), anyInt(), anyInt(), any());

    assertSame(order, orderRepository.findByIdInTenant(ORDER_ID, TENANT_ID));

    assertEquals(List.of("id", "tenantId"), capturedFields(orderRepository));
  }

  @Test
  void paymentLookupFailsClosedWithoutTenant() {
    assertNull(paymentRepository.findByIdInTenant(1L, null));
    assertNull(paymentRepository.findByIdInTenant(null, TENANT_ID));
    verify(paymentRepository, never()).queryByCondition(anyList(), any(), any(), any(), any());
  }

  @Test
  void paymentLookupAppliesIdAndTenantConditions() {
    Payment payment = samplePayment();
    doReturn(PageResult.of(List.of(payment), 1L, 1, 1))
        .when(paymentRepository)
        .queryByCondition(anyList(), any(), anyInt(), anyInt(), any());

    assertSame(payment, paymentRepository.findByIdInTenant(1L, TENANT_ID));

    assertEquals(List.of("id", "tenantId"), capturedFields(paymentRepository));
  }

  /** 捕获仓储下发的条件字段名，并要求每个字段都以 {@code EQ} 精确匹配（租户条件不得被弱化）。 */
  @SuppressWarnings("unchecked")
  private static List<String> capturedFields(com.bone.metadata.sdk.Repository<?, Long> repository) {
    ArgumentCaptor<List<QueryParam>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).queryByCondition(captor.capture(), any(), anyInt(), anyInt(), any());
    List<QueryParam> conditions = captor.getValue();
    conditions.forEach(param -> assertEquals(Operator.EQ, param.getType(), param.getField()));
    return conditions.stream().map(QueryParam::getField).toList();
  }

  private static Order sampleOrder() {
    OrderItem item = OrderItem.create(1L, ORDER_ID, 1L, "商品1", 2, new BigDecimal("100"));
    return Order.create(ORDER_ID, TENANT_ID, 200L, List.of(item));
  }

  private static Payment samplePayment() {
    return Payment.create(
        1L,
        TENANT_ID,
        ORDER_ID,
        200L,
        new BigDecimal("200"),
        PaymentChannel.SIMULATED,
        "https://pay.local/1");
  }
}
