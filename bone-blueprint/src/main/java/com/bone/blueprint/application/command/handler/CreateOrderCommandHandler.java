package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler {

    private final OrderRepository orderRepository;
    private final InventoryGateway inventoryGateway;
    private final OrderPriceCalculator priceCalculator;

    @Transactional
    public Long handle(CreateOrderCommand cmd) {
        for (CreateOrderCommand.OrderItemDto dto : cmd.getItems()) {
            if (!inventoryGateway.checkStock(dto.getProductId(), dto.getQuantity())) {
                throw new DomainException("商品库存不足: " + dto.getProductId());
            }
        }

        long orderId = DistributedIdGenerator.generateLongId();

        List<OrderItem> items = cmd.getItems().stream()
                .map(dto -> OrderItem.create(
                        DistributedIdGenerator.generateLongId(),
                        orderId,
                        dto.getProductId(),
                        dto.getProductName(),
                        dto.getQuantity(),
                        dto.getUnitPrice()))
                .collect(Collectors.toList());

        Order order = Order.create(orderId, cmd.getCustomerId(), items);

        BigDecimal finalPrice = priceCalculator.calculate(
                OrderPriceCalculator.OrderPriceRequest.builder()
                        .baseAmount(order.getTotalAmount())
                        .shippingFee(BigDecimal.ZERO)
                        .build()
        );
        order.updateTotalAmount(finalPrice);

        orderRepository.save(order);
        return order.getId();
    }
}
