package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.OrderItemRepository;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.shared.valueobject.Money;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
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

    // 构造期预分配 id 仅用于建模：持久化层 insert 时会重新分配主键并覆盖该值，
    // 故一切外键/事件引用都必须在订单落库后以 order.getId() 为准（明细 order_id 同理）。
    long provisionalOrderId = DistributedIdGenerator.generateLongId();
    long tenantId = tenantProvider.currentTenantId();

    List<OrderItem> items =
        cmd.items().stream()
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

    Order order = Order.create(provisionalOrderId, tenantId, cmd.customerId(), items);

    // 扩展点定价由应用层编排：算出最终金额后交给聚合，聚合不感知扩展点接口（领域层只认 Money）
    OrderPriceCalculator.OrderPriceRequest pricingRequest =
        OrderPriceCalculator.OrderPriceRequest.builder()
            .baseAmount(order.getTotalMoney().toBigDecimal())
            .build();
    // 扩展点代理按 BizContext（租户/业务维度）匹配具体实现；运行期上下文由扩展引擎 ThreadLocal 承载，
    // 应用层在调用扩展点前显式建立电商下单场景维度（bizCode=ecommerce/useCase=order/scenario=standard），
    // 命中平台默认计价器，try-with-resources 自动复原。
    BizContext<Void> pricingContext =
        BizContext.<Void>builder()
            .tenant(String.valueOf(tenantId))
            .bizCode("ecommerce")
            .useCase("order")
            .scenario("standard")
            .build();
    try (var scope = ExtensionContextManager.with(pricingContext)) {
      order.applyPricing(Money.of(priceCalculator.calculate(pricingRequest)));
    }

    orderRepository.save(order);
    // 落库后取回真实主键：insert 会重新分配并覆盖构造期预分配的 id，因此必须用落库后的 id
    // 校正身份与领域事件（create 阶段事件携带的是已被丢弃的预分配 id）。
    Long persistedOrderId = order.getId();
    order.rebindPersistedIdentity(persistedOrderId);

    // 明细无级联：须显式逐条持久化，否则 t_order_item 永不写入、下游库存预留静默失效。
    // 且明细 order_id 必须先回填落库后的真实订单 id——构造期绑定的是会被覆盖的预分配 id，
    // 直接用会让明细指向不存在的订单（孤儿行），findOrderWithItems 读不到明细。
    for (OrderItem item : items) {
      item.rebindOrderId(persistedOrderId);
      orderItemRepository.save(item);
    }
    domainEventPublisher.publishFrom(order);
    return persistedOrderId;
  }
}
