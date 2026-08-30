package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.shared.valueobject.Money;
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
  private final InventoryGateway inventoryGateway;
  private final OrderPriceCalculator priceCalculator;
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
  public Long handle(CreateOrderCommand cmd) {
    for (CreateOrderCommand.OrderItemDto dto : cmd.items()) {
      if (!inventoryGateway.checkStock(dto.productId(), dto.quantity())) {
        throw BizException.of("商品库存不足: " + dto.productId());
      }
    }

    long orderId = DistributedIdGenerator.generateLongId();
    long tenantId = tenantProvider.currentTenantId();

    List<OrderItem> items =
        cmd.items().stream()
            .map(
                dto ->
                    OrderItem.create(
                        DistributedIdGenerator.generateLongId(),
                        orderId,
                        dto.productId(),
                        dto.productName(),
                        dto.quantity(),
                        dto.unitPrice()))
            .collect(Collectors.toList());

    Order order = Order.create(orderId, tenantId, cmd.customerId(), items);

    // 扩展点定价由应用层编排：算出最终金额后交给聚合，聚合不感知扩展点接口（领域层只认 Money）
    OrderPriceCalculator.OrderPriceRequest pricingRequest =
        OrderPriceCalculator.OrderPriceRequest.builder()
            .baseAmount(order.getTotalMoney().toBigDecimal())
            .build();
    order.applyPricing(Money.of(priceCalculator.calculate(pricingRequest)));

    orderRepository.save(order);
    domainEventPublisher.publishFrom(order);
    return order.getId();
  }
}
