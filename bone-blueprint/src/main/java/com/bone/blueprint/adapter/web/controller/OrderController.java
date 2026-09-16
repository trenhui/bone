package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.web.dto.request.OrderPageQry;
import com.bone.blueprint.adapter.web.dto.response.CreateOrderResp;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.adapter.web.dto.response.OrderSummaryResp;
import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.handler.OrderPageQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.service.BlueprintIdempotencyService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理接口。
 *
 * <p><b>入站边界双形态（E-3.7 ApplicationService First）</b>： 简单用例（取消 / 发货 / 送达）走 {@link
 * OrderApplicationService}，复杂用例（创建订单，涉及扩展点）仍走独立 {@code CommandHandler}。一个用例只选一种构件。
 */
@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final CreateOrderCommandHandler createOrderCommandHandler;
  private final OrderApplicationService orderApplicationService;
  private final OrderDetailQueryHandler orderDetailQueryHandler;
  private final OrderPageQueryHandler orderPageQueryHandler;
  private final OrderAssembler orderAssembler;
  private final BlueprintIdempotencyService idempotencyService;

  @Operation(summary = "分页查询订单", description = "按客户、状态分页查询订单列表")
  @GetMapping
  public ApiResponse<PageResult<OrderSummaryResp>> page(@Valid @ModelAttribute OrderPageQry qry) {
    return ApiResponse.success(
        orderPageQueryHandler
            .handle(orderAssembler.toOrderPageQuery(qry))
            .map(orderAssembler::toOrderSummaryResp));
  }

  /**
   * 创建订单（支持 {@code Idempotency-Key} 幂等写）。
   *
   * <p><b>幂等放 adapter、机制在 application</b>：`Controller` 只做协议级编排——先问「这个键是否已有响应」，
   * 命中即原样返回；否则执行用例并把响应交给服务登记。机制（作用域键、指纹、TTL、冲突判定）全在 {@link BlueprintIdempotencyService}。这样比「让应用
   * Handler 返回 {@code ResponseEntity}」更贴合 E-10.1：应用层 不出现 HTTP 类型，Handler 仍返回纯用例结果。
   *
   * <p>只在成功路径记录快照：失败时客户端可用同一键重试（失败若也被记住，重试会永远拿回同一个错误响应）。
   */
  @Operation(
      summary = "创建订单",
      description =
          "创建新的订单，返回 201 与资源 Location。可带 Idempotency-Key：同键 + 同请求体重复提交返回同一响应；"
              + "同键 + 不同请求体返回 409 COMMON_IDEMPOTENCY_CONFLICT（API 规范 §6.1/§8）")
  @PostMapping
  public ResponseEntity<ApiResponse<CreateOrderResp>> create(
      @Parameter(description = "幂等键（UUID v4）；不传则不启用幂等")
          @RequestHeader(
              value = BlueprintIdempotencyService.IDEMPOTENCY_KEY_HEADER,
              required = false)
          String idempotencyKey,
      @Parameter(description = "订单创建请求") @Valid @RequestBody CreateOrderReq request,
      HttpServletRequest httpRequest) {
    CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
    String method = httpRequest.getMethod();
    String path = httpRequest.getRequestURI();

    Optional<ResponseEntity<ApiResponse<CreateOrderResp>>> replayed =
        idempotencyService.replay(idempotencyKey, method, path, command);
    if (replayed.isPresent()) {
      return replayed.get();
    }

    Long id = createOrderCommandHandler.handle(command);
    ResponseEntity<ApiResponse<CreateOrderResp>> response =
        ResponseEntity.created(URI.create("/api/v1/orders/" + id))
            .body(ApiResponse.success(CreateOrderResp.builder().id(id).build()));
    idempotencyService.remember(idempotencyKey, method, path, command, response);
    return response;
  }

  @Operation(summary = "取消订单", description = "取消指定的订单")
  @PostMapping("/{id}/cancel")
  public ApiResponse<Void> cancel(@Parameter(description = "订单ID") @PathVariable Long id) {
    orderApplicationService.cancel(orderAssembler.toCancelOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "订单发货", description = "对已支付订单发货（PAID → SHIPPED）")
  @PostMapping("/{id}/ship")
  public ApiResponse<Void> ship(@Parameter(description = "订单ID") @PathVariable Long id) {
    orderApplicationService.ship(orderAssembler.toShipOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "订单送达", description = "确认已发货订单送达（SHIPPED → DELIVERED）")
  @PostMapping("/{id}/deliver")
  public ApiResponse<Void> deliver(@Parameter(description = "订单ID") @PathVariable Long id) {
    orderApplicationService.deliver(orderAssembler.toDeliverOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
  @GetMapping("/{id}")
  public ApiResponse<OrderDetailResp> getById(
      @Parameter(description = "订单ID") @PathVariable Long id) {
    OrderDetailResp response =
        orderAssembler.toOrderDetailResp(orderDetailQueryHandler.handle(new OrderDetailQuery(id)));
    return ApiResponse.success(response);
  }
}
