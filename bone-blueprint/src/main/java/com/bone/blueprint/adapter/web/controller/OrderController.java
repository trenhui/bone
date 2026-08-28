package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.web.dto.request.OrderPageQry;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.adapter.web.dto.response.OrderSummaryResp;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.command.handler.DeliverOrderCommandHandler;
import com.bone.blueprint.application.command.handler.PayOrderCommandHandler;
import com.bone.blueprint.application.command.handler.ShipOrderCommandHandler;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.handler.OrderPageQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final CreateOrderCommandHandler createOrderCommandHandler;
  private final PayOrderCommandHandler payOrderCommandHandler;
  private final CancelOrderCommandHandler cancelOrderCommandHandler;
  private final ShipOrderCommandHandler shipOrderCommandHandler;
  private final DeliverOrderCommandHandler deliverOrderCommandHandler;
  private final OrderDetailQueryHandler orderDetailQueryHandler;
  private final OrderPageQueryHandler orderPageQueryHandler;
  private final OrderAssembler orderAssembler;

  @Operation(summary = "分页查询订单", description = "按客户、状态分页查询订单列表")
  @GetMapping
  public ApiResponse<PageResult<OrderSummaryResp>> page(@Valid @ModelAttribute OrderPageQry qry) {
    PageResult<OrderDto> page = orderPageQueryHandler.handle(orderAssembler.toOrderPageQuery(qry));
    List<OrderSummaryResp> rows =
        page.getList().stream().map(orderAssembler::toOrderSummaryResp).toList();
    return ApiResponse.success(
        PageResult.of(rows, page.getTotal(), page.getPageNum(), page.getPageSize()));
  }

  @Operation(summary = "创建订单", description = "创建新的订单，返回 201 与资源 Location")
  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
      @Parameter(description = "订单创建请求") @Valid @RequestBody CreateOrderReq request) {
    CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
    Long id = createOrderCommandHandler.handle(command);
    return ResponseEntity.created(URI.create("/api/v1/orders/" + id))
        .body(ApiResponse.success(Map.of("id", id)));
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

  @Operation(summary = "订单发货", description = "对已支付订单发货（PAID → SHIPPED）")
  @PostMapping("/{id}/ship")
  public ApiResponse<Void> ship(@Parameter(description = "订单ID") @PathVariable Long id) {
    shipOrderCommandHandler.handle(orderAssembler.toShipOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "订单送达", description = "确认已发货订单送达（SHIPPED → DELIVERED）")
  @PostMapping("/{id}/deliver")
  public ApiResponse<Void> deliver(@Parameter(description = "订单ID") @PathVariable Long id) {
    deliverOrderCommandHandler.handle(orderAssembler.toDeliverOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
  @GetMapping("/{id}")
  public ApiResponse<OrderDetailResp> getById(
      @Parameter(description = "订单ID") @PathVariable Long id) {
    OrderDetailQuery query = orderAssembler.toOrderDetailQuery(id);
    OrderDetailResp response =
        orderAssembler.toOrderDetailResp(orderDetailQueryHandler.handle(query));
    return ApiResponse.success(response);
  }
}
