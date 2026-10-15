package com.bone.aiorch.application;

import com.bone.ai.gateway.application.LlmGatewayService;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.airules.application.RuleQueryService;
import com.bone.airules.application.RuleService;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleEvalResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 编排执行器（第 1 批最小）：规则预检 → 短路判定 → LLM 步骤。
 *
 * <p>「规则命中不调 LLM」在此强制：任一 RULE_HIT_BLOCK 命中即跳过 LLM 步骤、不发请求、 不发事件，只产出短路信号供 trace 与上层消费。LLM
 * 请求由调用方携带（gateway 门面统一收口成本红线）。
 */
public class OrchestrationService {

  private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

  private final RuleService ruleService;
  private final RuleQueryService ruleQueryService;
  private final LlmGatewayService llmGatewayService;

  public OrchestrationService(
      RuleService ruleService,
      RuleQueryService ruleQueryService,
      LlmGatewayService llmGatewayService) {
    this.ruleService = ruleService;
    this.ruleQueryService = ruleQueryService;
    this.llmGatewayService = llmGatewayService;
  }

  /** 执行编排流水线。 */
  public OrchestrationResult run(OrchestrationRequest request) {
    List<RuleEvalResult> ruleResults = evaluateRules(request);
    boolean shortCircuited = ruleResults.stream().anyMatch(r -> r.hit() && r.shouldBlockLlm());
    LlmResponse llmResponse = null;
    if (request.llmRequest() != null && !shortCircuited) {
      llmResponse = llmGatewayService.invoke(request.llmRequest());
    } else if (shortCircuited) {
      log.info(
          "Orchestration short-circuited by rule hit: tenant={}, rules={}",
          request.tenantId(),
          ruleResults.stream().filter(RuleEvalResult::hit).map(RuleEvalResult::ruleCode).toList());
    }
    return OrchestrationResult.of(ruleResults, llmResponse);
  }

  /** 规则预检：指定码逐个评估；未指定 = 租户全部 ACTIVE。 */
  private List<RuleEvalResult> evaluateRules(OrchestrationRequest request) {
    List<RuleEvalResult> results = new ArrayList<>();
    if (request.ruleCodes().isEmpty()) {
      results.addAll(
          ruleService.evaluateAll(request.tenantId(), request.context(), request.trace()));
      return results;
    }
    for (String code : request.ruleCodes()) {
      ruleQueryService
          .findByCode(request.tenantId(), code)
          .map(RuleAsset::getId)
          .ifPresent(
              id -> results.add(ruleService.evaluate(id, request.context(), request.trace())));
    }
    return results;
  }
}
