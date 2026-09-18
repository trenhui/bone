package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.port.out.PricingService;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderItemRepository;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(
    name = "CreateOrder",
    description = "创建订单并保存到数据库",
    inputSchema =
        "{\"customerId\": \"long\", \"items\": [{\"productId\": \"long\", \"productName\":"
            + " \"string\", \"quantity\": \"int\", \"unitPrice\": \"decimal\"}]}",
    outputSchema = "{\"orderId\": \"long\"}",
    idempotent = false,
    cost = 3,
    retryable = false,
    timeout = 30)
public class CreateOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final InventoryGateway inventoryGateway;
  private final PricingService pricingService;
  private final TenantProvider tenantProvider;
  private final DomainEventPublisher domainEventPublisher;

  /**
   * 创建订单（写侧事务边界）。
   *
   * <p><b>库存策略（读同步、写异步）</b>：
   *
   * <ul>
   *   <li>{@code checkStock} 是同步<strong>读</strong>校验：超卖必须在下单前同步拦截，才能向用户返回可见的 「库存不足」错误，故保留在事务内。
   *   <li>{@code reserveStock} 是远程<strong>写</strong>：已从本事务移除。远程写置于本地事务会产生「库存 悬挂」（远程预留成功而本地回滚 →
   *       预留无对应订单），且远程慢会拖长事务、占用连接池。改由 {@code OrderCreatedEvent} 的 AFTER_COMMIT 订阅器异步执行 + 失败补偿对账（与
   *       {@code confirmStock}/{@code releaseStock} 的最终一致策略一致）。
   * </ul>
   */
  @Transactional
  public Long handle(CreateOrderCommand command) {
    for (CreateOrderCommand.OrderItemDto dto : command.items()) {
      if (!inventoryGateway.checkStock(dto.productId(), dto.quantity())) {
        throw BizException.of("商品库存不足: " + dto.productId());
      }
    }

    // 身份在构造期确定（ADR-0019 目标态）：SDK 尊重调用方预分配的非空 id，落库后 id 不变，
    // 因此明细外键与事件载荷可直接使用该 id，无需在落库后回填。
    long provisionalOrderId = DistributedIdGenerator.generateLongId();
    long tenantId = tenantProvider.currentTenantId();

    List<OrderItem> items =
        command.items().stream()
            .map(
                dto ->
                    OrderItem.create(
                        DistributedIdGenerator.generateLongId(),
                        provisionalOrderId,
                        dto.productId(),
                        dto.productName(),
                        dto.quantity(),
                        dto.unitPrice()))
            .collect(Collectors.toList());

    Order order = Order.create(provisionalOrderId, tenantId, command.customerId(), items);

    // 扩展点定价由应用层编排：算出最终金额后交给聚合，聚合不感知扩展点接口（领域层只认 Money）。
    // 上下文装配与扩展点调用收口到 PricingService 端口（实现在基础设施），应用层不再直连扩展引擎。
    order.applyPricing(pricingService.calculateFinalPrice(order.getTotalMoney(), tenantId));

    // ADR-0019 已落地：SDK 的 insert/save 对非空主键「空才生成、非空则尊重」，
    // 故预分配的 id 在落库后保持不变，无需再回填身份或重建事件（原 rebindPersistedIdentity 已删除）。
    orderRepository.save(order);
    Long persistedOrderId = order.getId();

    // 明细无级联：须显式逐条持久化，否则 t_order_item 永不写入、下游库存预留静默失效。
    // 明细 order_id 与订单 id 同源（构造期预分配值即最终值），无需二次绑定。
    for (OrderItem item : items) {
      orderItemRepository.save(item);
    }
    domainEventPublisher.publishFrom(order);
    return persistedOrderId;
  }
}
