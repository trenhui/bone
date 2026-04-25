package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "GetOrderDetail",
    description = "获取订单详情",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase implements UseCaseExecutor<OrderDetailQuery, OrderDto> {

    private final OrderDetailQueryHandler orderDetailQueryHandler;

    @Override
    public OrderDto execute(OrderDetailQuery query) {
        return orderDetailQueryHandler.handle(query);
    }
}
