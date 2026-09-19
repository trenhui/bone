package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.web.dto.request.OrderPageQry;
import com.bone.blueprint.adapter.web.dto.response.CreateOrderResp;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.adapter.web.dto.response.OrderSummaryResp;
import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.CreateOrderCommand;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单 Web 接口。
 *
 * <p><b>写侧走 {@link OrderApplicationService}（门面模式）</b>——控制器只依赖一个应用层入口，内部按用例复杂度决定是内联还是
 * 拆分，适配器不需要感知。读侧同样只依赖它；查询经 {@code application/query/port} 下沉到读侧适配器（CQRS 边界在 port 层）。
 *
 * <p><b>授权（API 规范 §9.2）</b>：端点声明 scope——读用 {@code order:orders:read}、写用 {@code
 * order:orders:write}；scope 来自 IAM 签发的 token（框架把 {@code scopes} claim 映射为 authority）。scope 目录见
 * README， 权限行由 {@code bone-init.sql} 种子提供并由管理员角色授予。
 *
 * <p><b>错误文档（API 规范 §12）</b>：每个端点用 {@code @ApiResponses} 标注典型 {@code errorCode}。注意 Swagger 的单数
 * 注解与统一信封类 {@link com.bone.core.model.ApiResponse} 同名，故注解写全限定名——直接 import 会与信封类冲突（编译器报 「对
 * ApiResponse 的引用不明确」）。
 */
@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderApplicationService orderApplicationService;
  private final OrderAssembler orderAssembler;
  private final IdempotencyService idempotencyService;

  @Operation(summary = "分页查询订单", description = "按客户、状态分页查询订单列表")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "BP_ORDER_STATUS_INVALID: 状态入参非法"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "未认证或凭证无效"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "缺少 order:orders:read")
  })
  @PreAuthorize("hasAuthority('order:orders:read')")
  @GetMapping
  public ApiResponse<PageResult<OrderSummaryResp>> page(@Valid @ModelAttribute OrderPageQry qry) {
    return ApiResponse.success(
        orderApplicationService
            .page(qry.getCustomerId(), qry.getStatus(), qry.getPageNum(), qry.getPageSize())
            .map(orderAssembler::toOrderSummaryResp));
  }

  /**
   * 创建订单（支持 {@code Idempotency-Key} 幂等写）。
   *
   * <p><b>幂等放 adapter、机制在 application</b>：`Controller` 只做协议级编排——先问「这个键是否已有响应」， 命中即渲染为 HTTP
   * 响应返回；否则执行用例并把响应事实交给服务登记。机制（作用域键、指纹、TTL、冲突判定）由 core 的 {@link IdempotencyService} 提供，它只以协议无关的
   * {@code ReplayedResponse} 交互，应用层不出现任何 HTTP 类型（E-10.1）。
   *
   * <p>只在成功路径记录快照：失败时客户端可用同一键重试（失败若也被记住，重试会永远拿回同一个错误响应）。
   */
  @Operation(
      summary = "创建订单",
      description =
          "创建新的订单，返回 201（资源 id 见 data.id）。可带 Idempotency-Key：同键 + 同请求体重复提交返回同一响应；"
              + "同键 + 不同请求体返回 409 COMMON_IDEMPOTENCY_CONFLICT（API 规范 §6.1/§8）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "请求参数不正确（校验失败）"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "缺少 order:orders:write"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "COMMON_IDEMPOTENCY_CONFLICT: 同一幂等键配不同请求体")
  })
  @PreAuthorize("hasAuthority('order:orders:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<CreateOrderResp> create(
      @Parameter(description = "幂等键（UUID v4）；不传则不启用幂等")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false)
          String idempotencyKey,
      @Parameter(description = "订单创建请求") @Valid @RequestBody CreateOrderReq request,
      HttpServletRequest httpRequest) {
    CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
    String method = httpRequest.getMethod();
    String path = httpRequest.getRequestURI();

    Optional<IdempotencyService.ReplayedResponse> replayed =
        idempotencyService.replay(idempotencyKey, MDC.get("userId"), method, path, command);
    if (replayed.isPresent()) {
      return toReplayedResponse(replayed.get());
    }

    Long id = orderApplicationService.create(command);
    // 信封 code 必须等于 HTTP 状态码（API 规范 §3.1/§3.2 的创建示例是 "code":201）；HTTP 201 由 @ResponseStatus 给出
    ApiResponse<CreateOrderResp> body =
        ApiResponse.success(HttpStatus.CREATED.value(), CreateOrderResp.builder().id(id).build());
    idempotencyService.remember(
        idempotencyKey,
        MDC.get("userId"),
        method,
        path,
        command,
        new IdempotencyService.ReplayedResponse(
            HttpStatus.CREATED.value(), "/api/v1/orders/" + id, body));
    return body;
  }

  /** 把协议无关的幂等快照渲染为统一信封——HTTP 状态码由 @ResponseStatus 决定（E-10.1），应用层不出现 HTTP 类型。 */
  @SuppressWarnings("unchecked")
  private static ApiResponse<CreateOrderResp> toReplayedResponse(
      IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<CreateOrderResp>) snapshot.body();
  }

  @Operation(summary = "取消订单", description = "取消指定的订单")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "BP_ORDER_NOT_FOUND: 订单不存在或不可见"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "BP_ORDER_STATUS_CONFLICT: 当前状态不允许取消"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "缺少 order:orders:write")
  })
  @PreAuthorize("hasAuthority('order:orders:write')")
  @PostMapping("/{id}/cancel")
  public ApiResponse<Void> cancel(@Parameter(description = "订单ID") @PathVariable Long id) {
    orderApplicationService.cancel(orderAssembler.toCancelOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "订单发货", description = "对已支付订单发货（PAID → SHIPPED）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "BP_ORDER_NOT_FOUND: 订单不存在或不可见"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "BP_ORDER_STATUS_CONFLICT: 当前状态不允许发货"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "缺少 order:orders:write")
  })
  @PreAuthorize("hasAuthority('order:orders:write')")
  @PostMapping("/{id}/ship")
  public ApiResponse<Void> ship(@Parameter(description = "订单ID") @PathVariable Long id) {
    orderApplicationService.ship(orderAssembler.toShipOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "订单送达", description = "确认已发货订单送达（SHIPPED → DELIVERED）")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "BP_ORDER_NOT_FOUND: 订单不存在或不可见"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "BP_ORDER_STATUS_CONFLICT: 当前状态不允许送达"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "缺少 order:orders:write")
  })
  @PreAuthorize("hasAuthority('order:orders:write')")
  @PostMapping("/{id}/deliver")
  public ApiResponse<Void> deliver(@Parameter(description = "订单ID") @PathVariable Long id) {
    orderApplicationService.deliver(orderAssembler.toDeliverOrderCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "BP_ORDER_NOT_FOUND: 订单不存在或不可见"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "缺少 order:orders:read")
  })
  @PreAuthorize("hasAuthority('order:orders:read')")
  @GetMapping("/{id}")
  public ApiResponse<OrderDetailResp> getById(
      @Parameter(description = "订单ID") @PathVariable Long id) {
    OrderDetailResp response =
        orderAssembler.toOrderDetailResp(orderApplicationService.getById(id));
    return ApiResponse.success(response);
  }
}
