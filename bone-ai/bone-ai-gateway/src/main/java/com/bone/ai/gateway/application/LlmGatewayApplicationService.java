package com.bone.ai.gateway.application;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.domain.gateway.PiiRedactor;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM 网关应用服务：模型调用的唯一入站边界（ADR-0028 Application Service First）。
 *
 * <p>职责——成本红线强制 + <b>出站 PII 脱敏</b> + 委派 {@link LlmClient} + 发布 {@link
 * LlmCallRecorded}（证据链发布端）。红线命中即抛业务异常且不触碰 client、不发事件（负向探针的地基）。
 *
 * <p><b>失败也留证（2026-10-17 复核修复）</b>：provider 超时 / 5xx / 熔断是事故复盘最需要的证据，捕获后补发失败事件再向上抛； 补发自身失败（如 trace
 * 落库失败）只记录日志，不用留证异常覆盖原始故障。
 *
 * <p><b>出口脱敏（OWASP LLM02:2025，2026-10-17 复核修复）</b>：个人信息出域到第三方模型即构成「向第三方提供个人信息」， 且证据链快照会长期留存
 * prompt/completion——两处都在门禁这一个出口统一处理，业务场景无需（也不应）各自记得脱敏：
 *
 * <ul>
 *   <li><b>出站</b>：请求消息脱敏后才交 {@link LlmClient}，模型只见到掩码值；
 *   <li><b>落证</b>：{@link LlmCallRecorded} 与响应内容同样脱敏，避免证据表成为「未脱敏 PII 的长期副本」。
 * </ul>
 *
 * 对业务侧仍返回原始响应（判定逻辑不变）；掩码保留首尾特征位，模型仍可做一致性比对，代价是模型看不到完整 PII—— 这是合规与模型能力的显式取舍，可在 {@link PiiRedactor}
 * 实现里调整策略。
 */
public class LlmGatewayApplicationService {

  private static final Logger log = LoggerFactory.getLogger(LlmGatewayApplicationService.class);

  private final LlmClient llmClient;

  private final DomainEventPublisher domainEventPublisher;
  private final TenantProvider tenantProvider;
  private final PiiRedactor piiRedactor;

  public LlmGatewayApplicationService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider) {
    // 无脱敏器即直通（脱敏器由装配层注入，application 不 new infrastructure 实现——DDD 分层）
    this(llmClient, domainEventPublisher, tenantProvider, null);
  }

  public LlmGatewayApplicationService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider,
      PiiRedactor piiRedactor) {
    this.llmClient = llmClient;
    this.domainEventPublisher = domainEventPublisher;
    this.tenantProvider = tenantProvider;
    this.piiRedactor = piiRedactor;
  }

  /**
   * 发起一次受治理的 LLM 调用（成本红线 + 出口脱敏）。
   *
   * @param request 模型请求
   * @return 模型响应（原文；仅出站与落证内容脱敏）
   * @throws BizException 当请求带成本红线信号（规则已命中）时抛出，且不会调用 {@link LlmClient}、不发布事件
   */
  public LlmResponse invoke(LlmRequest request) {
    if (request.shortCircuited()) {
      throw new BizException(400, "规则已命中，按成本红线跳过 LLM 调用", AiGatewayErrorCodes.REDLINE_BLOCKED);
    }
    LlmRequest outbound = redactRequest(request);
    LlmResponse response;
    try {
      response = llmClient.chat(outbound);
    } catch (RuntimeException e) {
      publishFailureEvidence(outbound, e);
      throw e;
    }
    // 证据链（发生了什么）与计量（花了多少）分离发布：spend tracking 消费者订阅 LlmUsageRecorded
    domainEventPublisher.publish(LlmCallRecorded.of(outbound, redactResponse(response)));
    domainEventPublisher.publish(
        LlmUsageRecorded.of(
            tenantProvider.currentTenantId(),
            request.model(),
            response.usage(),
            outbound.effectiveTraceId()));
    return response;
  }

  /** 出站请求脱敏（逐条消息文本；开关关闭时返回原请求，零拷贝语义）。 */
  private LlmRequest redactRequest(LlmRequest request) {
    if (piiRedactor == null || !piiRedactor.enabled()) {
      return request;
    }
    List<LlmMessage> messages =
        request.messages().stream()
            .map(m -> new LlmMessage(m.role(), piiRedactor.redact(m.content())))
            .toList();
    return new LlmRequest(
        request.model(),
        messages,
        request.temperature(),
        request.maxTokens(),
        request.jsonResponse(),
        request.shortCircuited(),
        request.traceId());
  }

  /** 落证响应脱敏（模型可能回显输入里的 PII，证据表不得成为未脱敏副本）。 */
  private LlmResponse redactResponse(LlmResponse response) {
    if (piiRedactor == null || !piiRedactor.enabled() || response == null) {
      return response;
    }
    return new LlmResponse(
        piiRedactor.redact(response.content()),
        response.model(),
        response.finishReason(),
        response.usage());
  }

  /** 失败调用补发证据（自身失败只告警，不覆盖原始故障——留证是尽力而为，拒返才是硬承诺）。 */
  private void publishFailureEvidence(LlmRequest request, RuntimeException cause) {
    try {
      domainEventPublisher.publish(
          LlmCallRecorded.ofFailure(
              request, cause.getClass().getSimpleName() + ": " + cause.getMessage()));
    } catch (RuntimeException publishFailure) {
      log.warn(
          "LLM failure evidence publish failed (original failure preserved): traceId={}",
          request.effectiveTraceId(),
          publishFailure);
    }
  }
}
