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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Tag(name = "支付管理", description = "提供下单支付、渠道回调、详情查询与退款的Web接口")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentApplicationService paymentApplicationService;
  private final PaymentAssembler paymentAssembler;

  /**
   * 回调来源白名单（逗号分隔的 IPv4/IPv6）。
   *
   * <p><b>为空 = 不限制来源</b>：这是<b>已知的失效形态</b>，不是"默认的安全设计"。它留着只为不打断未配置该键的 现网与本地联调，但绝不能把当安全措施 ——
   * 2026-10-05 起只要为空就会打 WARN，让"形同虚设"至少变成 <b>可见</b>。要真正兜住，正确形态是生产环境把它配成渠道出口 IP 段。
   */
  @Value("${bone.blueprint.payment.callback.allowed-source-ips:}")
  private String allowedSourceIpsRaw;

  /** 白名单是否已配置；用于启动后的可见性告警（不改变放行行为，避免打断未配置的现网）。 */
  private volatile boolean allowlistWarned;

  /**
   * 可信代理白名单（自家网关出口 IP）；为空=不采信「仅当来自可信代理」这层校验（兼容旧部署）。
   *
   * <p><b>为什么要它</b>：部署形态是「渠道 → bone-gateway → blueprint」，所以 {@code remoteAddr} 恒为网关 IP，真实渠道 IP 只存在于
   * {@code X-Forwarded-For}。但该头可由<strong>任何</strong>客户端 构造——只要能绕过网关直连本应用（端口暴露、内网横移、SSRF 落点），就能把任意
   * IP 写进白名单。 因此：<b>只有当请求本身来自可信代理时，才采信它转发的 XFF</b>。
   */
  @Value("${bone.blueprint.payment.callback.trusted-proxy-ips:}")
  private String trustedProxyIpsRaw;

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

  /**
   * 回调来源是否在白名单内（白名单为空则不限制，但会 WARN 一次）。
   *
   * <p><b>为什么保留"空 = 不限制"而不是直接拒绝</b>：改成拒绝会让所有<b>尚未配置该键</b>的环境立即 全量支付回调失败 ——
   * 那是资损级别的止血方式（把安全加固变成事故）。故此处只加可见性，真正的兜底 必须是运维把渠道出口 IP 配进来。
   *
   * <p><b>为什么"不限制"不能算防护</b>：本方法只看 {@code resolveClientIp} 的结果，而它采信 {@code X-Forwarded-For} 首段 ——
   * 该头可由任意客户端自行构造。要真正防住，必须引入 "仅当 remoteAddr 命中可信代理时才采信 XFF" 的可信代理白名单；那取决于支付渠道是<b>直连</b>应用
   * 还是<b>经自家网关</b>（前者 remoteAddr 就是渠道 IP，后者 remoteAddr 是网关 IP、白名单要配网关）， 属于部署拓扑决策，未在本次改动中臆定。
   */
  private boolean isSourceAllowed(HttpServletRequest request) {
    List<String> allowed = allowedSourceIps();
    if (allowed.isEmpty()) {
      warnAllowlistOnce();
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

  /** 白名单缺失告警：每次进程只打一次，避免刷日志。 */
  private void warnAllowlistOnce() {
    if (allowlistWarned) {
      return;
    }
    allowlistWarned = true;
    log.warn(
        "[支付回调] 未配置 bone.blueprint.payment.callback.allowed-source-ips ⇒"
            + "来源 IP 白名单未生效，支付回调入口对所有来源开放。本项依赖应用层验签，"
            + "但 X-Forwarded-For 可被任意客户端伪造，不能把白名单缺失当作已有防护。");
  }

  private String resolveClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    List<String> trustedProxies = trustedProxyIps();
    if (trustedProxies.isEmpty()) {
      // 未配置可信代理：保持旧行为（无条件采信 XFF 首段）。已配置时才有下面这层防伪造。
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
      return remoteAddr;
    }
    // 请求不是从可信代理来的 ⇒ 它自报的任何 XFF 都不作数（绕过网关直连 + 伪造头的组合在此失效）
    if (!isTrustedProxy(remoteAddr, trustedProxies)) {
      return remoteAddr;
    }
    // 来自可信代理：XFF 形如「真实客户端, 代理1, 代理2」，取**最左的非可信代理**地址
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      for (String hop : forwarded.split(",")) {
        String ip = hop.trim();
        if (!ip.isEmpty() && !isTrustedProxy(ip, trustedProxies)) {
          return ip;
        }
      }
    }
    return remoteAddr;
  }

  private static boolean isTrustedProxy(String ip, List<String> trustedProxies) {
    return ip != null && trustedProxies.contains(ip.trim());
  }

  private List<String> trustedProxyIps() {
    if (trustedProxyIpsRaw == null || trustedProxyIpsRaw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(trustedProxyIpsRaw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }
}
