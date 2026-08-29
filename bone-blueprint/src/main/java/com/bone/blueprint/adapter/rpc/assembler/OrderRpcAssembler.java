package com.bone.blueprint.adapter.rpc.assembler;

import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcReq;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import org.mapstruct.Mapper;

/**
 * RPC 入站 DTO → 应用层命令的 MapStruct 映射。
 *
 * <p>与 web 侧 {@code OrderAssembler} 职责对等（各自对应自己的入站契约），均复用 MapStruct 自动映射， 避免手写 stream 转换。
 */
@Mapper(componentModel = "spring")
public interface OrderRpcAssembler {

  CreateOrderCommand toCreateOrderCommand(CreateOrderRpcReq req);
}
