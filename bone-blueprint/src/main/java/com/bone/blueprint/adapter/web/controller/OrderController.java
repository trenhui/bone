
package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.req.CancelOrderRequest;
import com.bone.blueprint.adapter.web.dto.req.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.resp.OrderDetailResponse;
import com.bone.blueprint.adapter.web.dto.resp.OrderPageResponse;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.handler.OrderPageQueryHandler;
import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderCommandHandler createOrderCommandHandler;
    private final CancelOrderCommandHandler cancelOrderCommandHandler;
    private final OrderPageQueryHandler orderPageQueryHandler;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    private final OrderAssembler orderAssembler;

    @PostMapping
    public ApiResponse<OrderDetailResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        var command = orderAssembler.toCreateOrderCommand(request);
        var orderId = createOrderCommandHandler.handle(command);
        var detailQuery = orderAssembler.toOrderDetailQuery(orderId.getValue());
        var orderDto = orderDetailQueryHandler.handle(detailQuery);
        var response = orderAssembler.toOrderDetailResponse(orderDto);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String id, @Valid @RequestBody CancelOrderRequest request) {
        var command = orderAssembler.toCancelOrderCommand(id, request);
        cancelOrderCommandHandler.handle(command);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@PathVariable String id) {
        var query = orderAssembler.toOrderDetailQuery(id);
        var orderDto = orderDetailQueryHandler.handle(query);
        var response = orderAssembler.toOrderDetailResponse(orderDto);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<PageResult<OrderPageResponse>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status) {
        var query = orderAssembler.toOrderPageQuery(pageNo, pageSize, customerId, status);
        var pageResult = orderPageQueryHandler.handle(query);
        var response = orderAssembler.toOrderPageResponse(pageResult);
        return ApiResponse.success(response);
    }
}

