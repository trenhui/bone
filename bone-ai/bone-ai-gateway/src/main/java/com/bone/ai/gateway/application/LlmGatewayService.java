package com.bone.ai.gateway.application;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM 网关应用服务：模型调用的唯一入站边界（ADR-0028 Application Service First）。
 *
 * <p>职责——成本红线强制 + 委派 {@link LlmClient} + 发布 {@link LlmCallRecorded}（证据链发布端）。红线命中即抛业务异常且不触碰
 * client、不发事件（负向探针的地基）；路由 / 配额 / 审计 / 脱敏等治理语义作为后续批次叠加在门面之后。
 *
 * <p><b>失败也留证（2026-10-17 复核修复）</b>：provider 超时 / 5xx / 熔断是事故复盘最需要的证据，捕获后补发失败事件再向上抛； 补发自身失败（如 trace
 * 落库失败）只记录日志，不用留证异常覆盖原始故障。
 */
public class LlmGatewayService {

  private static final Logger log = LoggerFactory.getLogger(LlmGatewayService.class);

  /** 成本红线稳定业务码（前端 i18n / 监控聚合键，见 check-error-code-landing 门禁）。 */
  public static final String ERROR_CODE_REDLINE_BLOCKED = "AI_GATEWAY_REDLINE_BLOCKED";

  private final LlmClient llmClient;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantProvider tenantProvider;

  public LlmGatewayService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider) {
    this.llmClient = llmClient;
    this.domainEventPublisher = domainEventPublisher;
    this.tenantProvider = tenantProvider;
  }

  /**
   * 发起一次受治理的 LLM 调用。
   *
   * @param request 模型请求
   * @return 模型响应
   * @throws BizException 当请求带成本红线信号（规则已命中）时抛出，且不会调用 {@link LlmClient}、不发布事件
   */
  public LlmResponse invoke(LlmRequest request) {
    if (request.shortCircuited()) {
      throw new BizException(400, "规则已命中，按成本红线跳过 LLM 调用", ERROR_CODE_REDLINE_BLOCKED);
    }
    LlmResponse response;
    try {
      response = llmClient.chat(request);
    } catch (RuntimeException e) {
      publishFailureEvidence(request, e);
      throw e;
    }
    // 证据链（发生了什么）与计量（花了多少）分离发布：spend tracking 消费者订阅 LlmUsageRecorded
    domainEventPublisher.publish(LlmCallRecorded.of(request, response));
    domainEventPublisher.publish(
        LlmUsageRecorded.of(tenantProvider.currentTenantId(), request.model(), response.usage()));
    return response;
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
