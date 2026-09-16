package com.bone.blueprint.application;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.DeliverOrderCommand;
import com.bone.blueprint.application.command.cmd.ShipOrderCommand;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单用例边界（ApplicationService First）。承载单聚合、无远程调用、无两段式事务的简单用例。
 *
 * <p>复杂用例（创建订单涉及扩展点、发起支付需要两段式事务 + 远程调用）仍保留独立 CommandHandler。 一个用例只选一种构件（E-3.7）。
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

  private final OrderRepository orderRepository;

  /**
   * 领域事件发布器——当前仅 {@link #cancel(CancelOrderCommand)} 使用（Order.cancel() 发布 OrderCancelledEvent）。
   *
   * <p>{@link #ship(ShipOrderCommand)} / {@link #deliver(DeliverOrderCommand)}
   * 的领域方法不发布领域事件（内部状态迁移，无跨聚合协作）， 故不调用 publishFrom。若未来给 ship/deliver 加事件驱动能力（如发物流通知、触发用户评价），在对应方法追加
   * publishFrom 即可——提前注入避免后续改构造器 + 改 Mock。
   */
  private final DomainEventPublisher domainEventPublisher;

  private final TenantProvider tenantProvider;

  /**
   * 取消订单。加载订单 → 领域方法 {@code cancel()}（发布 OrderCancelledEvent）→ 保存发布。
   *
   * <p>CREATED / PAID 可取消，其余状态抛 {@link com.bone.core.exception.DomainException}。
   */
  @Transactional
  public void cancel(CancelOrderCommand command) {
    long tenantId = resolveTenantId(command.tenantId());
    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(command.orderId(), tenantId))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + command.orderId()));
    order.cancel();
    orderRepository.save(order);
    domainEventPublisher.publishFrom(order);
  }

  /**
   * 订单发货。加载订单 → 领域方法 {@code ship()}（仅 PAID 可发货）→ 保存。
   *
   * <p>ship() 是聚合内部状态迁移（无跨聚合协作），不发布领域事件。
   */
  @Transactional
  public void ship(ShipOrderCommand command) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(
                    command.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + command.orderId()));
    order.ship();
    orderRepository.save(order);
  }

  /**
   * 订单送达确认。加载订单 → 领域方法 {@code deliver()}（仅 SHIPPED 可送达）→ 保存。
   *
   * <p>deliver() 是聚合内部状态迁移（无跨聚合协作），不发布领域事件。
   */
  @Transactional
  public void deliver(DeliverOrderCommand command) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(
                    command.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + command.orderId()));
    order.deliver();
    orderRepository.save(order);
  }

  /**
   * 租户取值：命令显式携带优先（异步/定时任务入口），否则取当前请求上下文。
   *
   * <p>定时任务线程无请求上下文，{@code TenantProvider}
   * 会降级为平台租户（0）。若允许其静默回落，全租户扫描任务下发的命令会全部落到平台租户，表现为"任务在跑但一笔都没处理"。
   */
  private long resolveTenantId(Long explicitTenantId) {
    return explicitTenantId != null ? explicitTenantId : tenantProvider.currentTenantId();
  }
}
