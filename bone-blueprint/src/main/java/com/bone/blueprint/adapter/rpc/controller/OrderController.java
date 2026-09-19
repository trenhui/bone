package com.bone.blueprint.adapter.rpc.controller;

import com.bone.blueprint.adapter.rpc.assembler.OrderAssembler;
import com.bone.blueprint.adapter.rpc.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.rpc.dto.response.CreateOrderResp;
import com.bone.blueprint.adapter.rpc.dto.response.OrderDetailResp;
import com.bone.blueprint.application.OrderApplicationService;
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
 * <p>入站适配器（E-10.1）：仅做协议转换与路由，异常交由全局异常处理器统一处理——**不吞异常**（避免把失败伪装成 HTTP 200，掩盖真实错误导致调用方无法感知失败）。
 *
 * <p><b>为什么在 {@code controller} 子包</b>：三条 ArchUnit 门禁（{@code
 * adapterControllersMustNotDependOnGodObjects} / {@code ...OnDomainRepository} / {@code
 * ...OnDomainService}）按 {@code ..adapter..controller..} 匹配。RPC Controller 若平铺在 {@code adapter/rpc}
 * 下会**整条逃逸**——与 web 侧包形态对齐后门禁才生效（E-10 协议目录形态）。
 *
 * <p><b>为什么契约自持</b>：RPC 的协议 DTO 全部位于 {@code adapter/rpc/dto/**}，不反向依赖 {@code adapter/web} 的 DTO。
 * 两个平级入站适配器互相依赖会让契约演进互相牵制——web 面向人、RPC 面向服务，两者的演化节奏本就不同。
 *
 * <p><b>为什么类名不带 {@code Rpc}</b>：协议边界由包路径声明（{@code adapter/rpc/controller/}），类名只表达业务语义 （E-13.0）。与
 * web 侧 {@code adapter.web.controller.OrderController} 同名是**合法且预期**的——两者是不同协议下的
 * 同一个领域概念。类名不带协议标记，协议标识下沉到 DI 标识：显式 bean 名 {@code "rpcOrderController"} 避开 Spring 默认按类短名注册导致的
 * {@code ConflictingBeanDefinitionException}。 该约束由 {@code
 * BoneDddArchRules.springComponentBeanNamesMustBeUnique} 在构建期守护。
 */
@Slf4j
@Tag(name = "订单RPC服务", description = "提供订单相关的RPC接口，供其他服务调用")
@RestController("rpcOrderController")
@RequestMapping("/api/rpc/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderApplicationService orderApplicationService;
  private final OrderAssembler orderAssembler;

  @Operation(summary = "创建订单", description = "创建新的订单")
  @PostMapping
  public ApiResponse<CreateOrderResp> create(
      @Parameter(description = "订单创建请求") @RequestBody CreateOrderReq request) {
    Long orderId = orderApplicationService.create(orderAssembler.toCreateOrderCommand(request));

    CreateOrderResp response = new CreateOrderResp();
    response.setOrderId(orderId);
    response.setSuccess(true);
    response.setStatus("SUCCESS");
    return ApiResponse.success(response);
  }

  @Operation(summary = "根据ID查询订单", description = "根据订单ID查询订单详情")
  @GetMapping("/{orderId}")
  public ApiResponse<OrderDetailResp> getById(
      @Parameter(description = "订单ID") @PathVariable Long orderId) {
    return ApiResponse.success(
        orderAssembler.toOrderDetailResp(orderApplicationService.getById(orderId)));
  }
}
