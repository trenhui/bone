package com.bone.blueprint.adapter.rpc.assembler;

import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcReq;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import org.mapstruct.Mapper;

/**
 * RPC 入站 DTO → 应用层命令 / 出站响应映射。
 *
 * <p>入站：{@code CreateOrderRpcReq} → {@code CreateOrderCommand}；出站：{@code OrderDto} → {@link
 * OrderDetailResp}（**同服务内 web/rpc 复用同一响应 DTO**，避免重复定义契约，adapter 层 DTO 不透传到 application 之外）。
 */
@Mapper(componentModel = "spring")
public interface OrderRpcAssembler {

  CreateOrderCommand toCreateOrderCommand(CreateOrderRpcReq req);

  OrderDetailResp toOrderDetailResp(OrderDto dto);
}
