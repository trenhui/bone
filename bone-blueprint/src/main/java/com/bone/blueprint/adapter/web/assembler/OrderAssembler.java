package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderAssembler {
    
    CreateOrderCommand toCreateOrderCommand(CreateOrderRequest request);
    
    default PayOrderCommand toPayOrderCommand(Long orderId) {
        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(orderId);
        return command;
    }
    
    default CancelOrderCommand toCancelOrderCommand(Long orderId) {
        CancelOrderCommand command = new CancelOrderCommand();
        command.setOrderId(orderId);
        return command;
    }
    
    default OrderDetailQuery toOrderDetailQuery(Long orderId) {
        OrderDetailQuery query = new OrderDetailQuery();
        query.setOrderId(orderId);
        return query;
    }
    
    OrderDetailResponse toOrderDetailResponse(OrderDto orderDto);
}
