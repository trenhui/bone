package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.PaymentAssembler;
import com.bone.blueprint.adapter.web.dto.request.InitiatePaymentReq;
import com.bone.blueprint.adapter.web.dto.request.PaymentCallbackReq;
import com.bone.blueprint.adapter.web.dto.request.RefundPaymentReq;
import com.bone.blueprint.adapter.web.dto.response.InitiatePaymentResp;
import com.bone.blueprint.adapter.web.dto.response.PaymentDetailResp;
import com.bone.blueprint.application.command.handler.HandlePaymentCallbackCommandHandler;
import com.bone.blueprint.application.command.handler.InitiatePaymentCommandHandler;
import com.bone.blueprint.application.command.handler.RefundPaymentCommandHandler;
import com.bone.blueprint.application.command.result.InitiatePaymentResult;
import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.application.query.handler.PaymentDetailQueryHandler;
import com.bone.blueprint.application.query.qry.PaymentDetailQuery;
import com.bone.blueprint.domain.gateway.PaymentSignaturePort;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付 Web 接口：发起支付 + 支付回调 + 支付详情查询 + 退款。
 *
 * <p>Controller 仅做协议转换与路由（§15 adapter 职责），直接注入 {@code *CommandHandler}/{@code *QueryHandler}（§12
 * P0-7）。
 *
 * <p><b>回调验签在 adapter 边界完成</b>（支付样板 §3 + ADR-0022）：签名不可信直接拒绝，<strong>不进 Handler、不进领域</strong>。 覆盖
 * <strong>全部</strong>分支（成功 / 失败）——只验成功回调时，伪造的失败回调可把支付单打成 FAILED， 真实成功回调随后被聚合拒绝。
 */
@Tag(name = "支付管理", description = "提供下单支付、渠道回调、详情查询与退款的Web接口")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final InitiatePaymentCommandHandler initiatePaymentCommandHandler;
  private final HandlePaymentCallbackCommandHandler handlePaymentCallbackCommandHandler;
  private final RefundPaymentCommandHandler refundPaymentCommandHandler;
  private final PaymentDetailQueryHandler paymentDetailQueryHandler;
  private final PaymentAssembler paymentAssembler;
  private final PaymentSignaturePort paymentSignaturePort;

  @Operation(summary = "发起支付", description = "对指定订单发起支付，返回支付链接")
  @PostMapping("/initiate")
  public ApiResponse<InitiatePaymentResp> initiate(@Valid @RequestBody InitiatePaymentReq request) {
    InitiatePaymentResult result =
        initiatePaymentCommandHandler.handle(paymentAssembler.toInitiatePaymentCommand(request));
    return ApiResponse.success(paymentAssembler.toInitiatePaymentResp(result));
  }

  @Operation(summary = "支付回调", description = "支付渠道异步通知支付结果（先验签，再幂等确认）")
  @PostMapping("/callback")
  public ApiResponse<Void> callback(@Valid @RequestBody PaymentCallbackReq request) {
    // 验签前置到 adapter：成功与失败回调一律验签，不可信直接拒绝（防伪造失败回调把支付单打成终态）
    boolean trusted =
        paymentSignaturePort.verify(
            request.getPaymentId(),
            request.getChannelTradeNo(),
            request.getPaidAmount(),
            request.getSignature());
    if (!trusted) {
      throw new BizException("支付回调签名校验失败");
    }
    handlePaymentCallbackCommandHandler.handle(
        paymentAssembler.toHandlePaymentCallbackCommand(request));
    return ApiResponse.success();
  }

  @Operation(summary = "查询支付单", description = "查询支付单详情")
  @GetMapping("/{paymentId}")
  public ApiResponse<PaymentDetailResp> detail(@PathVariable Long paymentId) {
    PaymentDto dto = paymentDetailQueryHandler.handle(new PaymentDetailQuery(paymentId));
    return ApiResponse.success(paymentAssembler.toPaymentDetailResp(dto));
  }

  @Operation(summary = "退款", description = "对已成功支付单发起退款")
  @PostMapping("/{paymentId}/refund")
  public ApiResponse<Void> refund(
      @PathVariable Long paymentId, @Valid @RequestBody RefundPaymentReq request) {
    refundPaymentCommandHandler.handle(paymentAssembler.toRefundPaymentCommand(paymentId, request));
    return ApiResponse.success();
  }
}
