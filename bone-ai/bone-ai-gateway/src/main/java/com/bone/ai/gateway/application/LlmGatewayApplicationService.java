package com.bone.ai.gateway.application;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmStreamEvent;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import com.bone.ai.gateway.domain.gateway.ModelRouter;
import com.bone.ai.gateway.domain.gateway.PiiRedactor;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.domain.service.NoopLlmMetrics;
import com.bone.ai.gateway.domain.service.PassthroughModelRouter;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM 网关应用服务：模型调用的唯一入站边界（ADR-0028 Application Service First）。
 *
 * <p>职责——成本红线强制 + <b>出站 PII 脱敏</b> + 委派 {@link LlmClient} + 发布 {@link
 * LlmCallRecorded}（证据链发布端）。红线命中即抛业务异常且不触碰 client、不发事件（负向探针的地基）。
 *
 * <p>入口有两个形态：{@link #invoke}（缓冲 / 同步，绝大多数确定性判定场景）与 {@link #streamInvoke}（流式，长文逐步呈现 / 首 token 体验）；
 * 两者治理链一致，差异仅在末端——流式对每条增量做流式脱敏、在流末做内容安全 / 输出校验 / 缓存 / 计量 / 证据，并下发 {@link LlmStreamEvent}。
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
  private final LlmGatewayPolicies policies;

  private final ModelRouter modelRouter;

  public LlmGatewayApplicationService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider) {
    // 无策略即直通（策略由装配层注入，application 不 new infrastructure 实现——DDD 分层）
    this(llmClient, domainEventPublisher, tenantProvider, LlmGatewayPolicies.permissive());
  }

  /** 兼容旧装配：只配脱敏器，其余策略直通。 */
  public LlmGatewayApplicationService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider,
      PiiRedactor piiRedactor) {
    this(
        llmClient,
        domainEventPublisher,
        tenantProvider,
        new LlmGatewayPolicies(piiRedactor, null, null, null, null));
  }

  public LlmGatewayApplicationService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider,
      LlmGatewayPolicies policies) {
    this(
        llmClient,
        domainEventPublisher,
        tenantProvider,
        policies,
        PassthroughModelRouter.INSTANCE); // 无路由配置时的安全默认（按请求模型原样调用）
  }

  /**
   * 完整装配：显式指定模型档位路由（场景 → 模型）。
   *
   * <p>2026-10-21 补齐：{@link ModelRouter} 端口与 {@code ConfigDrivenModelRouter} 实现已就位，此前仅缺调用侧接线——
   * 端口配了却不消费＝「配了不生效」，配置驱动的档位路由（简单场景走廉价模型）形同虚设。
   */
  public LlmGatewayApplicationService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider,
      LlmGatewayPolicies policies,
      ModelRouter modelRouter) {
    this.llmClient = llmClient;
    this.domainEventPublisher = domainEventPublisher;
    this.tenantProvider = tenantProvider;
    this.policies = policies == null ? LlmGatewayPolicies.permissive() : policies;
    this.modelRouter = modelRouter == null ? PassthroughModelRouter.INSTANCE : modelRouter;
  }

  /**
   * 发起一次受治理的 LLM 调用，执行链： 红线短路 → 租户配额 → 提示注入检测（user 原文）→ 场景模型档位路由 → 出口脱敏 → 精确缓存查询 → 委派 client →
   * 输出内容安全 → 结构化输出校验 → 落证 + 计量 → 写缓存。
   *
   * <p>2026-10-21 补齐三处装配点（端口与策略组字段均已就位，此前仅缺调用侧接线）：场景路由（{@link ModelRouter}，成本红线 / 降档）、输出内容安全（{@code
   * ContentSafetyGuard}，OWASP LLM05/09）、可观测性发射（{@code LlmMetrics}，成本 / 延迟 / 错误率）。
   *
   * @param request 模型请求
   * @return 模型响应（原文；仅出站与落证内容脱敏）
   * @throws BizException 红线命中（400）/ 配额超限（429）/ 提示注入（400）/ 输出内容不安全（400）/ 结构化输出不合规（502）
   */
  public LlmResponse invoke(LlmRequest request) {
    // 租户先解析：短线路径也要记量化发射点（成本口径按租户聚合），故租户取在所有分支之前
    String tenantId = tenantProvider.currentTenantId();
    if (request.shortCircuited()) {
      metrics().recordShortCircuited(tenantId, request.model());
      throw new BizException(400, "规则已命中，按成本红线跳过 LLM 调用", AiGatewayErrorCodes.REDLINE_BLOCKED);
    }
    // 场景 → 模型档位路由（配置驱动；未声明场景 / 无映射即按请求模型原样调用）
    LlmRequest routed = routeByScenario(request);
    // 配额在脱敏与调用之前：超限租户不该消耗任何后续算力
    if (policies.quotaGuard() != null) {
      try {
        policies.quotaGuard().acquire(tenantId);
      } catch (BizException quotaExceeded) {
        metrics().recordQuotaExceeded(tenantId, routed.model());
        throw quotaExceeded;
      }
    }
    // 提示注入检测基于 user 原文（脱敏不改指令词，但检测原文最稳）：OWASP LLM01 第一关
    if (policies.injectionGuard() != null) {
      String traceId = routed.effectiveTraceId();
      for (LlmMessage message : routed.messages()) {
        if ("user".equalsIgnoreCase(message.role())) {
          policies.injectionGuard().check(message.content(), traceId);
        }
      }
    }
    LlmRequest outbound = redactRequest(routed);
    String cacheKey = cacheKeyOf(outbound, tenantId);
    if (cacheKey != null && policies.responseCache() != null) {
      Optional<LlmResponse> cached = policies.responseCache().get(cacheKey);
      if (cached.isPresent()) {
        metrics().recordCacheHit(tenantId, outbound.model());
        // 命中未触达模型：不发证据 / 计量（证据链只记真实发生的调用），只留可观测日志
        log.info("LLM 精确缓存命中: traceId={}, model={}", outbound.effectiveTraceId(), outbound.model());
        return cached.get();
      }
    }
    LlmResponse response;
    long callStartNanos = System.nanoTime();
    try {
      response = llmClient.chat(outbound);
    } catch (RuntimeException e) {
      metrics().recordProviderCall(tenantId, outbound.model(), false, elapsedMs(callStartNanos));
      publishFailureEvidence(outbound, e);
      throw e;
    }
    metrics().recordProviderCall(tenantId, outbound.model(), true, elapsedMs(callStartNanos));
    // 输出内容安全（OWASP LLM05/09）：模型产出不可接受即拒绝——命中也留证，便于事后复盘越狱尝试
    if (policies.contentSafetyGuard() != null) {
      try {
        policies.contentSafetyGuard().check(response.content(), outbound.effectiveTraceId());
      } catch (RuntimeException unsafe) {
        publishFailureEvidence(outbound, unsafe);
        throw unsafe;
      }
    }
    // 结构化输出校验：下游按 JSON 解析，格式坏了就是无归因故障，在网关挡住并给稳定码
    if (outbound.jsonResponse() && policies.outputValidator() != null) {
      try {
        policies.outputValidator().validate(response.content(), outbound.effectiveTraceId());
      } catch (RuntimeException invalid) {
        publishFailureEvidence(outbound, invalid);
        throw invalid;
      }
    }
    if (cacheKey != null && policies.responseCache() != null) {
      policies.responseCache().put(cacheKey, response);
    }
    LlmUsage usage = response.usage();
    if (usage != null) {
      metrics()
          .recordTokens(
              tenantId,
              outbound.model(),
              usage.promptTokens(),
              usage.completionTokens(),
              usage.totalTokens());
    }
    // 证据链（发生了什么）与计量（花了多少）分离发布：spend tracking 消费者订阅 LlmUsageRecorded
    domainEventPublisher.publish(LlmCallRecorded.of(outbound, redactResponse(response)));
    domainEventPublisher.publish(
        LlmUsageRecorded.of(
            tenantId, outbound.model(), response.usage(), outbound.effectiveTraceId()));
    return response;
  }

  /**
   * 发起一次受治理的<b>流式</b> LLM 调用，治理链与 {@link #invoke} 一致，差异在末端：
   *
   * <ul>
   *   <li>每条 {@link LlmStreamEvent.Token} 以<b>原文</b>下发（与 {@code invoke}
   *       同口径：脱敏只作用于出站请求与证据落库，不改写业务答案）； 同时累积全文，供流末治理使用。
   *   <li>全文在流结束时落地治理：内容安全（OWASP LLM05/09）/ 结构化输出校验 / 精确缓存写入 / 计量（TTFT + token）/ 证据发布； 其中 {@link
   *       LlmCallRecorded} 证据在发布前按出站口径脱敏（PII / 提示注入不落明文证据）。
   *   <li>首 token 到达即发射 {@code recordFirstToken}（TTFT 体验指标）；
   *   <li>provider 错误 / 治理拦截经 {@link LlmStreamEvent.Error} 下发并留证，调用方据此停止并兜底。
   * </ul>
   *
   * <p><b>为何不做逐片流式脱敏</b>：一段 PII 可能恰被 SSE 切在两 chunk 之间，逐片正则对单边都「不像完整 PII」而漏脱敏；而累积重脱敏又会在每条增量上回吐
   * {@code [手机号]} 这类证据标签，违背「面向调用方返回原文」的契约。 故流式与 {@code invoke}
   * 一致——调用方拿原文，脱敏只在出站请求与证据落库两处发生；对必须零泄露的含 PII 请求，治理层可强制走缓冲（非流式）模式（详设 §2.1）。
   *
   * <p>与 {@code invoke} 同口径：红线短路、租户配额、提示注入检测（user 原文）、场景路由、出站脱敏、缓存命中（确定性请求）均在流开始前完成。
   *
   * @param request 模型请求
   * @param sink 流式事件接收器（Token / Done / Error）
   */
  public void streamInvoke(LlmRequest request, Consumer<LlmStreamEvent> sink) {
    String tenantId = tenantProvider.currentTenantId();
    if (request.shortCircuited()) {
      metrics().recordShortCircuited(tenantId, request.model());
      throw new BizException(400, "规则已命中，按成本红线跳过 LLM 调用", AiGatewayErrorCodes.REDLINE_BLOCKED);
    }
    LlmRequest routed = routeByScenario(request);
    if (policies.quotaGuard() != null) {
      try {
        policies.quotaGuard().acquire(tenantId);
      } catch (BizException quotaExceeded) {
        metrics().recordQuotaExceeded(tenantId, routed.model());
        throw quotaExceeded;
      }
    }
    if (policies.injectionGuard() != null) {
      String traceId = routed.effectiveTraceId();
      for (LlmMessage message : routed.messages()) {
        if ("user".equalsIgnoreCase(message.role())) {
          policies.injectionGuard().check(message.content(), traceId);
        }
      }
    }
    LlmRequest outbound = redactRequest(routed);
    String cacheKey = cacheKeyOf(outbound, tenantId);
    if (cacheKey != null && policies.responseCache() != null) {
      Optional<LlmResponse> cached = policies.responseCache().get(cacheKey);
      if (cached.isPresent()) {
        metrics().recordCacheHit(tenantId, outbound.model());
        LlmResponse c = cached.get();
        sink.accept(LlmStreamEvent.token(c.content()));
        sink.accept(LlmStreamEvent.done(c.model(), c.finishReason(), c.usage()));
        return;
      }
    }

    StringBuilder accumulated = new StringBuilder();
    long callStartNanos = System.nanoTime();
    boolean[] firstToken = {true};
    // 流末才确定的量（模型 / 结束原因 / 用量）需跨 lambda 可变，用数组持有（lambda 捕获要求有效 final）
    final LlmUsage[] finalUsage = {LlmUsage.empty()};
    final String[] finalModel = {outbound.model()};
    final String[] finalFinish = {"stop"};
    try {
      llmClient.streamChat(
          outbound,
          event -> {
            if (event instanceof LlmStreamEvent.Token t) {
              if (firstToken[0]) {
                firstToken[0] = false;
                metrics().recordFirstToken(tenantId, outbound.model(), elapsedMs(callStartNanos));
              }
              // 与 invoke 口径一致：返回给调用方的响应为原文（脱敏只作用于出站请求与证据落库，不改写业务答案）；
              // 此处累计原文用于流末内容安全 / 输出校验 / 证据组装
              accumulated.append(t.delta());
              sink.accept(LlmStreamEvent.token(t.delta()));
            } else if (event instanceof LlmStreamEvent.Done d) {
              finalUsage[0] = d.usage() != null ? d.usage() : LlmUsage.empty();
              finalModel[0] = d.model() != null ? d.model() : outbound.model();
              finalFinish[0] = d.finishReason() != null ? d.finishReason() : "stop";
            }
            // Error 事件在 catch 之外无法拦截底层流异常；此处仅记录 Done 之外的终止，错误见下方 catch
          });
    } catch (RuntimeException e) {
      metrics().recordProviderCall(tenantId, outbound.model(), false, elapsedMs(callStartNanos));
      publishFailureEvidence(outbound, e);
      sink.accept(LlmStreamEvent.error(e.getMessage(), e));
      return;
    }

    String fullContent = accumulated.toString();
    // 流末治理：内容安全 / 结构化输出校验（与 invoke 同口径，但作用在全文而非单条消息）
    if (policies.contentSafetyGuard() != null) {
      try {
        policies.contentSafetyGuard().check(fullContent, outbound.effectiveTraceId());
      } catch (RuntimeException unsafe) {
        publishFailureEvidence(outbound, unsafe);
        sink.accept(LlmStreamEvent.error(unsafe.getMessage(), unsafe));
        return;
      }
    }
    if (outbound.jsonResponse() && policies.outputValidator() != null) {
      try {
        policies.outputValidator().validate(fullContent, outbound.effectiveTraceId());
      } catch (RuntimeException invalid) {
        publishFailureEvidence(outbound, invalid);
        sink.accept(LlmStreamEvent.error(invalid.getMessage(), invalid));
        return;
      }
    }

    metrics().recordProviderCall(tenantId, outbound.model(), true, elapsedMs(callStartNanos));
    if (finalUsage[0] != null) {
      metrics()
          .recordTokens(
              tenantId,
              finalModel[0],
              finalUsage[0].promptTokens(),
              finalUsage[0].completionTokens(),
              finalUsage[0].totalTokens());
    }
    LlmResponse assembled =
        new LlmResponse(fullContent, finalModel[0], finalFinish[0], finalUsage[0]);
    if (cacheKey != null && policies.responseCache() != null) {
      policies.responseCache().put(cacheKey, assembled);
    }
    domainEventPublisher.publish(LlmCallRecorded.of(outbound, redactResponse(assembled)));
    domainEventPublisher.publish(
        LlmUsageRecorded.of(tenantId, finalModel[0], finalUsage[0], outbound.effectiveTraceId()));
    sink.accept(LlmStreamEvent.done(finalModel[0], finalFinish[0], finalUsage[0]));
  }

  /**
   * 场景 → 模型档位路由：命中映射用档位模型，否则按请求模型原样调用。
   *
   * <p>「该用多贵的模型」从调用方硬编码收敛为平台可配置策略——调用方只声明业务场景，具体档位由 {@link ModelRouter} 解析。
   */
  private LlmRequest routeByScenario(LlmRequest request) {
    String scenario = request.scenario();
    if (scenario == null || scenario.isBlank()) {
      return request;
    }
    String resolved = modelRouter.resolveModel(scenario, request.model());
    if (resolved == null || resolved.equals(request.model())) {
      return request;
    }
    log.info("模型档位路由: scenario={}, {} → {}", scenario, request.model(), resolved);
    return request.withModel(resolved);
  }

  /** 量化发射点（缺省 Noop：零依赖，不观测该维度）。 */
  private LlmMetrics metrics() {
    LlmMetrics metrics = policies.metrics();
    return metrics == null ? NoopLlmMetrics.INSTANCE : metrics;
  }

  /** 毫秒级耗时（provider 延迟口径）。 */
  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  /**
   * 缓存键：仅确定性请求（temperature 为 0 或未指定）参与缓存。
   *
   * <p>temperature &gt; 0 的输出本就随机，缓存会改变业务语义（同一请求两次判定不同才是预期）——故返回 null 表示不缓存。
   */
  /**
   * 缓存键：仅确定性请求（temperature 为 0 或未指定）参与缓存。
   *
   * <p><b>租户必须进键（2026-10-19 修复）</b>：此前键只由模型参数构成，两个租户的相同提问（脱敏后必然相同， 因为 PII 已被掩码）会命中同一条缓存——等于把 A
   * 租户的模型响应返给 B 租户。即便模型只见掩码值、 响应理论上只含掩码内容，业务语义仍是租户私有的（保单结论、额度、理赔意见），跨租户共享即数据隔离失守。
   * 这是多租户系统对缓存的基本要求，与「缓存是否默认开启」无关。
   *
   * <p>temperature &gt; 0 的输出本就随机，缓存会改变业务语义（同一请求两次判定不同才是预期）——故返回 null 表示不缓存。
   */
  private String cacheKeyOf(LlmRequest request, String tenantId) {
    if (request.temperature() != null && request.temperature() > 0) {
      return null;
    }
    try {
      Map<String, Object> canonical = new LinkedHashMap<>();
      canonical.put("tenantId", tenantId);
      canonical.put("model", request.model());
      canonical.put("messages", request.messages());
      canonical.put("temperature", request.temperature());
      canonical.put("maxTokens", request.maxTokens());
      canonical.put("jsonResponse", request.jsonResponse());
      return sha256(new ObjectMapper().writeValueAsString(canonical));
    } catch (JsonProcessingException e) {
      log.warn("缓存键计算失败，跳过缓存: {}", e.getMessage());
      return null;
    }
  }

  /** 出站请求脱敏（逐条消息文本；开关关闭时返回原请求，零拷贝语义）。 */
  private LlmRequest redactRequest(LlmRequest request) {
    PiiRedactor redactor = policies.piiRedactor();
    if (redactor == null || !redactor.enabled()) {
      return request;
    }
    List<LlmMessage> messages =
        request.messages().stream()
            .map(m -> new LlmMessage(m.role(), redactor.redact(m.content())))
            .toList();
    return new LlmRequest(
        request.model(),
        messages,
        request.temperature(),
        request.maxTokens(),
        request.jsonResponse(),
        request.shortCircuited(),
        request.traceId(),
        request.scenario());
  }

  /** 落证响应脱敏（模型可能回显输入里的 PII，证据表不得成为未脱敏副本）。 */
  private LlmResponse redactResponse(LlmResponse response) {
    PiiRedactor redactor = policies.piiRedactor();
    if (redactor == null || !redactor.enabled() || response == null) {
      return response;
    }
    return new LlmResponse(
        redactor.redact(response.content()),
        response.model(),
        response.finishReason(),
        response.usage());
  }

  private static String sha256(String input) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
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
