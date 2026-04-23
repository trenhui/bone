package com.bone.blueprint.adapter.rpc;

import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcRequest;
import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcResponse;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.usecase.standard.CreateOrderUseCase;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单RPC服务
 * <p>
 * 提供订单相关的RPC接口，供其他服务调用
 * </p>
 */
@Tag(name = "订单RPC服务", description = "提供订单相关的RPC接口，供其他服务调用")
@RestController
@RequestMapping("/api/rpc/orders")
@RequiredArgsConstructor
public class OrderRpcService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderRpcService.class);
    
    private final CreateOrderUseCase createOrderUseCase;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    
    /**
     * 创建订单
     * 
     * @param request 订单创建请求
     * @return 创建结果
     */
    @Operation(summary = "创建订单", description = "创建新的订单")
    @PostMapping
    public ApiResponse<CreateOrderRpcResponse> createOrder(@Parameter(description = "订单创建请求") @RequestBody CreateOrderRpcRequest request) {
        try {
            log.info("收到创建订单RPC请求: customerId={}, itemsCount={}", 
                    request.getCustomerId(), request.getItems().size());
            
            // 转换RPC请求为命令对象
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
            
            Long orderId = createOrderUseCase.execute(command);
            
            CreateOrderRpcResponse response = new CreateOrderRpcResponse();
            response.setOrderId(orderId);
            response.setSuccess(true);
            response.setStatus("SUCCESS");
            
            log.info("创建订单成功: orderId={}", orderId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            CreateOrderRpcResponse response = new CreateOrderRpcResponse();
            response.setSuccess(false);
            response.setErrorMsg(e.getMessage());
            response.setStatus("FAILED");
            return ApiResponse.success(response);
        }
    }
    
    /**
     * 根据ID查询订单
     * 
     * @param orderId 订单ID
     * @return 订单详情
     */
    @Operation(summary = "根据ID查询订单", description = "根据订单ID查询订单详情")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto> getOrderById(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        try {
            log.info("收到查询订单RPC请求: orderId={}", orderId);
            
            OrderDetailQuery query = new OrderDetailQuery();
            query.setOrderId(orderId);
            OrderDto orderDto = orderDetailQueryHandler.handle(query);
            
            if (orderDto != null) {
                log.info("查询订单成功: orderId={}", orderId);
            } else {
                log.warn("订单不存在: orderId={}", orderId);
            }
            
            return ApiResponse.success(orderDto);
        } catch (Exception e) {
            log.error("查询订单失败: orderId={}", orderId, e);
            return ApiResponse.success(null);
        }
    }
}
