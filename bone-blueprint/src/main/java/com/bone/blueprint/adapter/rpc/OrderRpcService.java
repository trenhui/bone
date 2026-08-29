package com.bone.blueprint.adapter.rpc;

import com.bone.blueprint.adapter.rpc.assembler.OrderRpcAssembler;
import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcReq;
import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcResp;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单 RPC 服务：供其他服务调用。
 *
 * <p>入站适配器（§15）：仅做协议转换与路由，异常交由全局异常处理器统一处理——**不吞异常**（避免把失败伪装成 HTTP 200，掩盖真实错误导致调用方无法感知失败）。
 */
@Slf4j
@Tag(name = "订单RPC服务", description = "提供订单相关的RPC接口，供其他服务调用")
@RestController
@RequestMapping("/api/rpc/orders")
@RequiredArgsConstructor
public class OrderRpcService {

  private final CreateOrderCommandHandler createOrderCommandHandler;
  private final OrderDetailQueryHandler orderDetailQueryHandler;
  private final OrderRpcAssembler orderRpcAssembler;

  @Operation(summary = "创建订单", description = "创建新的订单")
  @PostMapping
  public ApiResponse<CreateOrderRpcResp> createOrder(
      @Parameter(description = "订单创建请求") @RequestBody CreateOrderRpcReq request) {
    Long orderId =
        createOrderCommandHandler.handle(orderRpcAssembler.toCreateOrderCommand(request));

    CreateOrderRpcResp response = new CreateOrderRpcResp();
    response.setOrderId(orderId);
    response.setSuccess(true);
    response.setStatus("SUCCESS");
    return ApiResponse.success(response);
  }

  @Operation(summary = "根据ID查询订单", description = "根据订单ID查询订单详情")
  @GetMapping("/{orderId}")
  public ApiResponse<OrderDetailResp> getOrderById(
      @Parameter(description = "订单ID") @PathVariable Long orderId) {
    // 与 web 侧统一：不可变查询对象 + 复用同一响应 DTO（同服务内 web/rpc 契约一致）
    OrderDto dto = orderDetailQueryHandler.handle(new OrderDetailQuery(orderId));
    return ApiResponse.success(orderRpcAssembler.toOrderDetailResp(dto));
  }
}
