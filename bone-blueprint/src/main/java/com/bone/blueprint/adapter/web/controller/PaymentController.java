package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.PaymentAssembler;
import com.bone.blueprint.adapter.web.dto.request.InitiatePaymentReq;
import com.bone.blueprint.adapter.web.dto.request.PaymentCallbackReq;
import com.bone.blueprint.adapter.web.dto.request.RefundPaymentReq;
import com.bone.blueprint.adapter.web.dto.response.InitiatePaymentResp;
import com.bone.blueprint.adapter.web.dto.response.PaymentDetailResp;
import com.bone.blueprint.application.PaymentApplicationService;
import com.bone.blueprint.application.command.InitiatePaymentResult;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付 Web 接口。
 *
 * <p><b>入站边界（ADR-0028 应用服务化）</b>：三个写端点一律走 {@link PaymentApplicationService}——本模块已无 {@code
 * CommandHandler}，发起支付（两段式远程调用 + 事务拆分）与支付回调（验签 + 幂等状态机）的编排都在应用服务内完成。
 *
 * <p><b>授权（API 规范 §9.2）</b>：用户侧端点声明 scope——读用 {@code order:payment:read}、写用 {@code
 * order:payment:write}（来自 IAM 签发的 token，框架把 {@code scopes} claim 映射为 authority）。回调端点为支付渠道
 * server-to-server 调用，不要求用户 scope（{@code permitAll}），依赖<b>应用服务内验签</b> + 本处<b>来源 IP 白名单</b>纵深防御。
 *
 * <p><b>回调验签在应用服务内完成</b>（{@code PaymentApplicationService#processCallback}，签名经 {@code
 * PaymentSignaturePort} 校验）：签名不可信直接拒绝，不进领域。覆盖全部分支（成功 / 失败）——只验成功回调时，伪造的失败回调 可把支付单打成
 * FAILED，真实成功回调随后被聚合拒绝。
 */
@Tag(name = "支付管理", description = "提供下单支付、渠道回调、详情查询与退款的Web接口")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentApplicationService paymentApplicationService;
  private final PaymentAssembler paymentAssembler;

  /** 回调来源白名单（逗号分隔的 IPv4/IPv6）；为空表示不限制来源（默认，便于联调）。 */
  @Value("${bone.blueprint.payment.callback.allowed-source-ips:}")
  private String allowedSourceIpsRaw;

  @Operation(summary = "发起支付", description = "对指定订单发起支付，返回支付链接")
  @PreAuthorize("hasAuthority('order:payment:write')")
  @PostMapping("/initiate")
  public ApiResponse<InitiatePaymentResp> initiate(@Valid @RequestBody InitiatePaymentReq request) {
    InitiatePaymentResult result =
        paymentApplicationService.initiate(paymentAssembler.toInitiatePaymentCommand(request));
    return ApiResponse.success(paymentAssembler.toInitiatePaymentResp(result));
  }

  @Operation(summary = "支付回调", description = "支付渠道异步通知支付结果（验签已收口到应用服务；来源须命中白名单）")
  @PreAuthorize("permitAll()")
  @PostMapping("/callback")
  public ApiResponse<Void> callback(
      @Valid @RequestBody PaymentCallbackReq request, HttpServletRequest httpRequest) {
    if (!isSourceAllowed(httpRequest)) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.PAYMENT_CALLBACK_SOURCE_NOT_ALLOWED, "回调来源不在白名单");
    }
    paymentApplicationService.processCallback(
        paymentAssembler.toProcessPaymentCallbackCommand(request));
    return ApiResponse.success();
  }

  @Operation(summary = "查询支付单", description = "按支付单ID查询详情")
  @PreAuthorize("hasAuthority('order:payment:read')")
  @GetMapping("/{paymentId}")
  public ApiResponse<PaymentDetailResp> getById(@PathVariable Long paymentId) {
    return ApiResponse.success(
        paymentAssembler.toPaymentDetailResp(paymentApplicationService.getById(paymentId)));
  }

  @Operation(summary = "退款", description = "对已成功支付单发起退款")
  @PreAuthorize("hasAuthority('order:payment:write')")
  @PostMapping("/{paymentId}/refund")
  public ApiResponse<Void> refund(
      @PathVariable Long paymentId, @Valid @RequestBody RefundPaymentReq request) {
    paymentApplicationService.refund(paymentAssembler.toRefundPaymentCommand(paymentId, request));
    return ApiResponse.success();
  }

  /** 回调来源是否在白名单内（白名单为空则不限制）。 */
  private boolean isSourceAllowed(HttpServletRequest request) {
    List<String> allowed = allowedSourceIps();
    if (allowed.isEmpty()) {
      return true;
    }
    String source = resolveClientIp(request);
    return allowed.contains(source);
  }

  private List<String> allowedSourceIps() {
    if (allowedSourceIpsRaw == null || allowedSourceIpsRaw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(allowedSourceIpsRaw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
