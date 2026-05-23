package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.command.handler.PayOrderCommandHandler;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderCommandHandler createOrderCommandHandler;
    private final PayOrderCommandHandler payOrderCommandHandler;
    private final CancelOrderCommandHandler cancelOrderCommandHandler;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    private final OrderAssembler orderAssembler;

    @Operation(summary = "创建订单", description = "创建新的订单")
    @PostMapping
    public ApiResponse<Long> create(
            @Parameter(description = "订单创建请求") @Valid @RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
        return ApiResponse.success(createOrderCommandHandler.handle(command));
    }

    @Operation(summary = "支付订单", description = "支付指定的订单")
    @PostMapping("/{id}/pay")
    public ApiResponse<Void> pay(@Parameter(description = "订单ID") @PathVariable Long id) {
        PayOrderCommand command = orderAssembler.toPayOrderCommand(id);
        payOrderCommandHandler.handle(command);
        return ApiResponse.success();
    }

    @Operation(summary = "取消订单", description = "取消指定的订单")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@Parameter(description = "订单ID") @PathVariable Long id) {
        CancelOrderCommand command = orderAssembler.toCancelOrderCommand(id);
        cancelOrderCommandHandler.handle(command);
        return ApiResponse.success();
    }

    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@Parameter(description = "订单ID") @PathVariable Long id) {
        OrderDetailQuery query = orderAssembler.toOrderDetailQuery(id);
        OrderDetailResponse response = orderAssembler.toOrderDetailResponse(orderDetailQueryHandler.handle(query));
        return ApiResponse.success(response);
    }
}
