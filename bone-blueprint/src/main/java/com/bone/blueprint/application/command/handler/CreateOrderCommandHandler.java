package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.support.AggregatePersistence;
import com.bone.blueprint.application.support.TenantSupport;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.valueobject.Money;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
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
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public Long handle(CreateOrderCommand cmd) {
        for (CreateOrderCommand.OrderItemDto dto : cmd.getItems()) {
            if (!inventoryGateway.checkStock(dto.getProductId(), dto.getQuantity())) {
                throw BizException.of("商品库存不足: " + dto.getProductId());
            }
        }

        long orderId = DistributedIdGenerator.generateLongId();
        long tenantId = TenantSupport.currentTenantId();

        List<OrderItem> items = cmd.getItems().stream()
                .map(dto -> OrderItem.create(
                        DistributedIdGenerator.generateLongId(),
                        orderId,
                        dto.getProductId(),
                        dto.getProductName(),
                        dto.getQuantity(),
                        dto.getUnitPrice()))
                .collect(Collectors.toList());

        Order order = Order.create(orderId, tenantId, cmd.getCustomerId(), items);

        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(order.getTotalAmount())
                .shippingFee(BigDecimal.ZERO)
                .build();
        BigDecimal finalPrice = priceCalculator.calculate(request);
        order.updateTotalAmount(Money.of(finalPrice));

        for (OrderItem item : order.getItems()) {
            inventoryGateway.reserveStock(orderId, item.getProductId(), item.getQuantity());
        }

        return AggregatePersistence.saveAndPublishEvents(orderRepository, domainEventPublisher, order);
    }
}
