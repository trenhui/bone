package com.bone.blueprint.application.event;

import com.bone.blueprint.application.integration.OrderIntegrationEventPublisher;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.application.support.OrderLookup;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单支付成功后续处理（AFTER_COMMIT）：确认库存扣减 + 发布集成事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

    private final OrderRepository orderRepository;
    private final InventoryGateway inventoryGateway;
    private final OrderIntegrationEventPublisher integrationEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPaidEvent event) {
        log.info("订单支付成功: orderId={}", event.orderId());

        Order order = OrderLookup.requireById(orderRepository, event.orderId());
        for (OrderItem item : order.getItems()) {
            inventoryGateway.confirmStock(event.orderId(), item.getProductId(), item.getQuantity());
        }

        integrationEventPublisher.publishOrderPaid(
                OrderPaidIntegrationEvent.fromDomain(
                        event.orderId(), event.customerId(), event.amount(), event.occurredAt()));
    }
}
