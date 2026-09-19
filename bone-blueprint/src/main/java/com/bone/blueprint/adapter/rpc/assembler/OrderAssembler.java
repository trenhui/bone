package com.bone.blueprint.adapter.rpc.assembler;

import com.bone.blueprint.adapter.rpc.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.rpc.dto.response.OrderDetailResp;
import com.bone.blueprint.application.command.CreateOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import org.mapstruct.Mapper;

/**
 * RPC 入站 DTO → 应用层命令 / 出站响应映射。
 *
 * <p>入站：{@code CreateOrderReq} → {@code CreateOrderCommand}；出站：{@code OrderDto} → {@link
 * OrderDetailResp}。**两端都是本适配器自持的协议 DTO**（{@code adapter/rpc/dto/**}）：adapter 层 DTO 不外泄到
 * application，也不反向借用 {@code adapter/web} 的 DTO（见 E-10.1 转换边界）。
 *
 * <p><b>为什么接口名不带 {@code Rpc} 而 {@link Mapper#implementationName()} 带</b>：协议边界由包路径声明 （{@code
 * adapter/rpc/assembler/}），类名只表达业务语义（E-13.0）。与 web 侧 {@code adapter.web.assembler.OrderAssembler}
 * 同名合法，但两者的生成实现默认都叫 {@code OrderAssemblerImpl}—— 在同一个模块里会按类短名注册成两个 {@code orderAssemblerImpl}
 * bean。故协议标识下沉到生成类名， 由 {@code implementationName} 显式指定，避开 {@code
 * ConflictingBeanDefinitionException}。
 */
@Mapper(componentModel = "spring", implementationName = "RpcOrderAssemblerImpl")
public interface OrderAssembler {

  CreateOrderCommand toCreateOrderCommand(CreateOrderReq req);

  OrderDetailResp toOrderDetailResp(OrderDto dto);
}
