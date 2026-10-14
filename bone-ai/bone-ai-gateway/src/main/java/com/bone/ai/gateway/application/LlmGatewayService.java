package com.bone.ai.gateway.application;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;

/**
 * LLM 网关应用服务：模型调用的唯一入站边界（ADR-0028 Application Service First）。
 *
 * <p>职责——成本红线强制 + 委派 {@link LlmClient} + 发布 {@link LlmCallRecorded}（证据链发布端）。红线命中即抛业务异常且不触碰
 * client、不发事件（负向探针的地基）；路由 / 配额 / 审计 / 脱敏等治理语义作为后续批次叠加在门面之后。
 */
public class LlmGatewayService {

  /** 成本红线稳定业务码（前端 i18n / 监控聚合键，见 check-error-code-landing 门禁）。 */
  public static final String ERROR_CODE_REDLINE_BLOCKED = "AI_GATEWAY_REDLINE_BLOCKED";

  private final LlmClient llmClient;
  private final DomainEventPublisher domainEventPublisher;

  public LlmGatewayService(LlmClient llmClient, DomainEventPublisher domainEventPublisher) {
    this.llmClient = llmClient;
    this.domainEventPublisher = domainEventPublisher;
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
    LlmResponse response = llmClient.chat(request);
    domainEventPublisher.publish(LlmCallRecorded.of(request, response));
    return response;
  }
}
