package com.bone.aiorch.application;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.aiorch.domain.decision.DecisionGate;
import com.bone.aiorch.domain.decision.DecisionVerdict;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleEvalResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 编排执行器：规则预检 → 短路判定 → LLM 步骤 → 确定性闸门裁决。
 *
 * <p>「规则命中不调 LLM」在此强制：任一 RULE_HIT_BLOCK 命中即跳过 LLM 步骤、不发请求、 不发事件，只产出短路信号供 trace 与上层消费。
 *
 * <p><b>全链 traceId（2026-10-17 复核修复）</b>：编排入口生成 / 透传同一 traceId，注入规则灰度分桶上下文、绑定 LLM 请求，
 * 使证据快照（ai_trace_snapshot.trace_id）可按一次业务动作拼回全链——此前每次 LLM 调用各生成 UUID，回放拼不出全景。
 *
 * <p><b>AI 故障不阻断业务 + 确定性闸门（2026-10-18，对齐 ClaimSphere 确定性分层）</b>：LLM 调用失败（超时 / 熔断 / 5xx / 红线）
 * 不再向上抛出，而是记为 {@code aiError} 并由 {@link DecisionGate} 裁定转人工——AI 只是建议来源，不该成为业务可用性的单点。
 * 闸门同时覆盖置信度不足与金额超阈值两条熔断线。
 */
public class OrchestrationApplicationService {

  private static final Logger log = LoggerFactory.getLogger(OrchestrationApplicationService.class);

  /** 规则灰度分桶键（RuleAsset.inGrayBucket 按此取稳定分桶）。 */
  public static final String CONTEXT_TRACE_ID = "traceId";

  private final RuleApplicationService ruleService;
  private final RuleQueryApplicationService ruleQueryService;
  private final LlmGatewayApplicationService llmGatewayService;
  private final DecisionGate decisionGate;

  /** 兼容构造：使用默认阈值（置信度 0.85 / 金额 5000）。 */
  public OrchestrationApplicationService(
      RuleApplicationService ruleService,
      RuleQueryApplicationService ruleQueryService,
      LlmGatewayApplicationService llmGatewayService) {
    this(ruleService, ruleQueryService, llmGatewayService, new DecisionGate());
  }

  public OrchestrationApplicationService(
      RuleApplicationService ruleService,
      RuleQueryApplicationService ruleQueryService,
      LlmGatewayApplicationService llmGatewayService,
      DecisionGate decisionGate) {
    this.ruleService = ruleService;
    this.ruleQueryService = ruleQueryService;
    this.llmGatewayService = llmGatewayService;
    this.decisionGate = decisionGate == null ? new DecisionGate() : decisionGate;
  }

  /** 执行编排流水线。 */
  public OrchestrationResult run(OrchestrationRequest request) {
    String traceId = request.effectiveTraceId();
    List<RuleEvalResult> ruleResults = evaluateRules(request, traceId);
    boolean shortCircuited = ruleResults.stream().anyMatch(r -> r.hit() && r.shouldBlockLlm());
    boolean hasLlmStep = request.llmRequest() != null && !shortCircuited;
    LlmResponse llmResponse = null;
    String aiError = null;
    if (hasLlmStep) {
      try {
        llmResponse = llmGatewayService.invoke(request.llmRequest().withTraceId(traceId));
      } catch (RuntimeException e) {
        // 架构红线：AI 推理失败不得阻断业务流程——记录降级事实，交由闸门转人工 / 确定性路径
        aiError = e.getClass().getSimpleName() + ": " + e.getMessage();
        log.warn(
            "LLM 步骤失败，降级为确定性路径: tenant={}, traceId={}, cause={}",
            request.tenantId(),
            traceId,
            aiError);
      }
    } else if (shortCircuited) {
      log.info(
          "Orchestration short-circuited by rule hit: tenant={}, traceId={}, rules={}",
          request.tenantId(),
          traceId,
          ruleResults.stream().filter(RuleEvalResult::hit).map(RuleEvalResult::ruleCode).toList());
    }
    DecisionVerdict verdict =
        decisionGate.evaluate(request.confidence(), request.amount(), aiError == null, hasLlmStep);
    if (verdict.requiresHuman()) {
      log.info(
          "决策需人工终审: tenant={}, traceId={}, reasons={}",
          request.tenantId(),
          traceId,
          verdict.reasons());
    }
    return OrchestrationResult.of(ruleResults, llmResponse, verdict, aiError);
  }

  /** 规则预检：指定码逐个评估；未指定 = 租户全部 ACTIVE。 */
  private List<RuleEvalResult> evaluateRules(OrchestrationRequest request, String traceId) {
    Map<String, Object> context = withTraceId(request.context(), traceId);
    List<RuleEvalResult> results = new ArrayList<>();
    if (request.ruleCodes().isEmpty()) {
      results.addAll(ruleService.evaluateAll(request.tenantId(), context, request.trace()));
      return results;
    }
    for (String code : request.ruleCodes()) {
      ruleQueryService
          .findByCode(request.tenantId(), code)
          .map(RuleAsset::getId)
          .ifPresent(id -> results.add(ruleService.evaluate(id, context, request.trace())));
    }
    return results;
  }

  /** 保证灰度分桶键存在（同一业务动作内多次编排判定稳定）。 */
  private static Map<String, Object> withTraceId(Map<String, Object> context, String traceId) {
    Object existing = context.get(CONTEXT_TRACE_ID);
    if (existing != null && !String.valueOf(existing).isBlank()) {
      return context;
    }
    Map<String, Object> merged = new HashMap<>(context);
    merged.put(CONTEXT_TRACE_ID, traceId);
    return Map.copyOf(merged);
  }
}
