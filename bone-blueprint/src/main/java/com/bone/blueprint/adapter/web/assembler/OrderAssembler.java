package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.web.dto.request.OrderPageQry;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.adapter.web.dto.response.OrderSummaryResp;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.DeliverOrderCommand;
import com.bone.blueprint.application.command.cmd.ShipOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderAssembler {

  CreateOrderCommand toCreateOrderCommand(CreateOrderReq request);

  OrderPageQuery toOrderPageQuery(OrderPageQry qry);

  OrderSummaryResp toOrderSummaryResp(OrderDto orderDto);

  default CancelOrderCommand toCancelOrderCommand(Long orderId) {
    // HTTP 入口：租户由请求上下文提供，命令不携带（定时任务入口才显式传 tenantId）
    return new CancelOrderCommand(orderId, null);
  }

  default ShipOrderCommand toShipOrderCommand(Long orderId) {
    return new ShipOrderCommand(orderId);
  }

  default DeliverOrderCommand toDeliverOrderCommand(Long orderId) {
    return new DeliverOrderCommand(orderId);
  }

  OrderDetailResp toOrderDetailResp(OrderDto orderDto);
}
