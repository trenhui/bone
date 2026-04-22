
package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.core.result.PageResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<OrderDto> handle(OrderPageQuery query) {
        QueryBuilder<Order> builder = QueryBuilder.from(Order.class);

        if (query.getCustomerId() != null) {
            builder.where("customerId").eq(query.getCustomerId());
        }
        if (query.getStatus() != null) {
            builder.where("status").eq(query.getStatus());
        }

        return builder.page(query.getPageNo(), query.getPageSize())
                .mapTo(OrderDto.class);
    }
}

