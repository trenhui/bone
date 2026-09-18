package com.bone.blueprint.adapter.rpc.assembler;

import com.bone.blueprint.adapter.rpc.dto.request.CreateOrderRpcReq;
import com.bone.blueprint.adapter.rpc.dto.response.OrderDetailResp;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import org.mapstruct.Mapper;

/**
 * RPC 入站 DTO → 应用层命令 / 出站响应映射。
 *
 * <p>入站：{@code CreateOrderRpcReq} → {@code CreateOrderCommand}；出站：{@code OrderDto} → {@link
 * OrderDetailResp}。**两端都是本适配器自持的协议 DTO**（{@code adapter/rpc/dto/**}）：adapter 层 DTO 不外泄到
 * application，也不反向借用 {@code adapter/web} 的 DTO（见 E-10.1 转换边界）。
 */
@Mapper(componentModel = "spring")
public interface OrderRpcAssembler {

  CreateOrderCommand toCreateOrderCommand(CreateOrderRpcReq req);

  OrderDetailResp toOrderDetailResp(OrderDto dto);
}
