package com.bone.blueprint.adapter.rpc;

import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcReq;
import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcResp;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 订单 RPC 服务：供其他服务调用。 */
@Slf4j
@Tag(name = "订单RPC服务", description = "提供订单相关的RPC接口，供其他服务调用")
@RestController
@RequestMapping("/api/rpc/orders")
@RequiredArgsConstructor
public class OrderRpcService {

    private final CreateOrderCommandHandler createOrderCommandHandler;
    private final OrderDetailQueryHandler orderDetailQueryHandler;

    @Operation(summary = "创建订单", description = "创建新的订单")
    @PostMapping
    public ApiResponse<CreateOrderRpcResp> createOrder(
            @Parameter(description = "订单创建请求") @RequestBody CreateOrderRpcReq request) {
        try {
            List<CreateOrderCommand.OrderItemDto> items = request.getItems().stream()
                    .map(item -> CreateOrderCommand.OrderItemDto.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .build())
                    .collect(Collectors.toList());

            CreateOrderCommand command = CreateOrderCommand.builder()
                    .customerId(request.getCustomerId())
                    .items(items)
                    .build();

            Long orderId = createOrderCommandHandler.handle(command);

            CreateOrderRpcResp response = new CreateOrderRpcResp();
            response.setOrderId(orderId);
            response.setSuccess(true);
            response.setStatus("SUCCESS");
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("创建订单失败: customerId={}", request.getCustomerId(), e);
            CreateOrderRpcResp response = new CreateOrderRpcResp();
            response.setSuccess(false);
            response.setErrorMsg(e.getMessage());
            response.setStatus("FAILED");
            return ApiResponse.success(response);
        }
    }

    @Operation(summary = "根据ID查询订单", description = "根据订单ID查询订单详情")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto> getOrderById(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        OrderDetailQuery query = new OrderDetailQuery();
        query.setOrderId(orderId);
        return ApiResponse.success(orderDetailQueryHandler.handle(query));
    }
}
