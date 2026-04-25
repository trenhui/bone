package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.usecase.simple.CancelOrderUseCase;
import com.bone.blueprint.application.usecase.simple.GetOrderDetailUseCase;
import com.bone.blueprint.application.usecase.standard.CreateOrderUseCase;
import com.bone.blueprint.application.usecase.standard.PayOrderUseCase;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final PayOrderUseCase payOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;
    private final OrderAssembler orderAssembler;

    @Operation(summary = "创建订单", description = "创建新的订单")
    @PostMapping
    public ApiResponse<Long> create(@Parameter(description = "订单创建请求") @Valid @RequestBody CreateOrderRequest request) {
        try {
            log.info("收到创建订单请求: customerId={}, itemsCount={}", 
                    request.getCustomerId(), request.getItems().size());
            
            CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
            Long orderId = createOrderUseCase.execute(command);
            
            log.info("创建订单成功: orderId={}", orderId);
            return ApiResponse.success(orderId);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            throw e;
        }
    }

    @Operation(summary = "支付订单", description = "支付指定的订单")
    @PostMapping("/{id}/pay")
    public ApiResponse<Void> pay(@Parameter(description = "订单ID") @PathVariable Long id) {
        try {
            log.info("收到支付订单请求: orderId={}", id);
            
            PayOrderCommand command = orderAssembler.toPayOrderCommand(id);
            payOrderUseCase.execute(command);
            
            log.info("支付订单成功: orderId={}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("支付订单失败: orderId={}", id, e);
            throw e;
        }
    }

    @Operation(summary = "取消订单", description = "取消指定的订单")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@Parameter(description = "订单ID") @PathVariable Long id) {
        try {
            log.info("收到取消订单请求: orderId={}", id);
            
            CancelOrderCommand command = orderAssembler.toCancelOrderCommand(id);
            cancelOrderUseCase.execute(command);
            
            log.info("取消订单成功: orderId={}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("取消订单失败: orderId={}", id, e);
            throw e;
        }
    }

    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@Parameter(description = "订单ID") @PathVariable Long id) {
        try {
            log.info("收到查询订单详情请求: orderId={}", id);
            
            OrderDetailQuery query = orderAssembler.toOrderDetailQuery(id);
            var orderDto = getOrderDetailUseCase.execute(query);
            var response = orderAssembler.toOrderDetailResponse(orderDto);
            
            log.info("查询订单详情成功: orderId={}", id);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("查询订单详情失败: orderId={}", id, e);
            throw e;
        }
    }
}
