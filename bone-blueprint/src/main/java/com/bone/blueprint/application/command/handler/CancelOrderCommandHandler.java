package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 订单取消用例：加载订单 → 领域方法 {@code cancel()}（发布 OrderCancelledEvent）→ 保存发布。 */
@Capability(
    name = "CancelOrder",
    description = "取消订单（CREATED/PAID 可取消）",
    inputSchema = "{\"orderId\": \"long\"}",
    outputSchema = "{}",
    idempotent = false,
    cost = 2,
    retryable = false,
    timeout = 10)
@Component
@RequiredArgsConstructor
public class CancelOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantProvider tenantProvider;

  @Transactional
  public void handle(CancelOrderCommand cmd) {
    long tenantId = resolveTenantId(cmd.tenantId());
    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(cmd.orderId(), tenantId))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + cmd.orderId()));
    order.cancel();
    orderRepository.save(order);
    domainEventPublisher.publishFrom(order);
  }

  /**
   * 租户取值：命令显式携带优先（异步/定时任务入口，E-4.4），否则取当前请求上下文。
   *
   * <p>定时任务线程无请求上下文，{@code TenantProvider} 会降级为平台租户（0）。若允许其静默回落，
   * 全租户扫描任务下发的命令会全部落到平台租户，表现为"任务在跑但一笔都没处理"。
   */
  private long resolveTenantId(Long explicitTenantId) {
    return explicitTenantId != null ? explicitTenantId : tenantProvider.currentTenantId();
  }
}
