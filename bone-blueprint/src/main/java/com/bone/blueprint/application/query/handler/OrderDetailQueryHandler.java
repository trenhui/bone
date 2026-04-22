
package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderDetailQueryHandler {

    @Transactional(readOnly = true)
    public OrderDto handle(OrderDetailQuery query) {
        return QueryBuilder.from(Order.class)
                .where("id").eq(query.getOrderId())
                .single()
                .mapTo(OrderDto.class);
    }
}

