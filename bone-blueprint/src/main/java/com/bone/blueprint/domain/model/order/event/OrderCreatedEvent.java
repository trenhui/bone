
package com.bone.blueprint.domain.model.order.event;

import com.bone.blueprint.domain.model.order.vo.OrderId;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCreatedEvent {

    private final OrderId orderId;
    private final String customerId;
}

