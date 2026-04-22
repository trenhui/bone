
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.vo.OrderId;
import com.bone.blueprint.domain.model.order.vo.OrderItem;
import com.bone.blueprint.domain.model.order.vo.ShippingAddress;
import com.bone.blueprint.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderId handle(CreateOrderCommand cmd) {
        List&lt;OrderItem&gt; orderItems = cmd.getItems().stream()
                .map(item -&gt; OrderItem.of(
                        item.getProductId(),
                        item.getProductName(),
                        item.getPrice(),
                        item.getQuantity()
                ))
                .collect(Collectors.toList());

        ShippingAddress address = ShippingAddress.of(
                cmd.getShippingAddress().getRecipientName(),
                cmd.getShippingAddress().getPhone(),
                cmd.getShippingAddress().getProvince(),
                cmd.getShippingAddress().getCity(),
                cmd.getShippingAddress().getDistrict(),
                cmd.getShippingAddress().getDetail(),
                cmd.getShippingAddress().getPostalCode()
        );

        Order order = Order.create(cmd.getCustomerId(), orderItems, address, cmd.getRemark());
        orderRepository.save(order);
        return order.getId();
    }
}

