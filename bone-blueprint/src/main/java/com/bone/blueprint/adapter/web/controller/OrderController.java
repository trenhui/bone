package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.command.handler.PayOrderCommandHandler;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderCommandHandler createOrderCommandHandler;
    private final PayOrderCommandHandler payOrderCommandHandler;
    private final CancelOrderCommandHandler cancelOrderCommandHandler;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    private final OrderAssembler orderAssembler;

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CreateOrderRequest request) {
        var command = orderAssembler.toCreateOrderCommand(request);
        var orderId = createOrderCommandHandler.handle(command);
        return ApiResponse.success(orderId);
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<Void> pay(@PathVariable Long id) {
        var command = orderAssembler.toPayOrderCommand(id);
        payOrderCommandHandler.handle(command);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        var command = orderAssembler.toCancelOrderCommand(id);
        cancelOrderCommandHandler.handle(command);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@PathVariable Long id) {
        var query = orderAssembler.toOrderDetailQuery(id);
        var orderDto = orderDetailQueryHandler.handle(query);
        var response = orderAssembler.toOrderDetailResponse(orderDto);
        return ApiResponse.success(response);
    }
}
