package com.bone.aiorch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.airules.application.RuleQueryService;
import com.bone.airules.application.RuleService;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleEvalResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** OrchestrationService：规则预检 → 短路 → LLM 的最小流水线语义。 */
class OrchestrationServiceTest {

  private RuleService ruleService;
  private RuleQueryService ruleQueryService;
  private LlmGatewayService llmGatewayService;
  private OrchestrationService service;

  private static final LlmRequest LLM_REQUEST =
      LlmRequest.of("qwen3.5", List.of(new LlmMessage("user", "审查")));

  @BeforeEach
  void setUp() {
    ruleService = mock(RuleService.class);
    ruleQueryService = mock(RuleQueryService.class);
    llmGatewayService = mock(LlmGatewayService.class);
    service = new OrchestrationService(ruleService, ruleQueryService, llmGatewayService);
  }

  @Test
  void short_circuits_when_rule_hit_with_block() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(ruleQueryService.findByCode("t1", "R01")).thenReturn(Optional.of(rule));
    when(ruleService.evaluate("id-1", Map.of("amount", 15000.0), true))
        .thenReturn(RuleEvalResult.hit("R01", 1, "trace", 5, true));

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest(
                "t1", List.of("R01"), Map.of("amount", 15000.0), LLM_REQUEST, true));

    assertThat(result.shortCircuited()).isTrue();
    assertThat(result.blockedRuleCode()).isEqualTo("R01");
    assertThat(result.llmResponse()).isNull();
    verify(llmGatewayService, never()).invoke(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void invokes_llm_when_no_rule_hit() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(ruleQueryService.findByCode("t1", "R01")).thenReturn(Optional.of(rule));
    when(ruleService.evaluate("id-1", Map.of("amount", 500.0), true))
        .thenReturn(RuleEvalResult.miss("R01", 1, "trace", 4));
    LlmResponse response = new LlmResponse("审查通过", "qwen3.5", "stop", new LlmUsage(10, 5, 15));
    when(llmGatewayService.invoke(LLM_REQUEST)).thenReturn(response);

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest(
                "t1", List.of("R01"), Map.of("amount", 500.0), LLM_REQUEST, true));

    assertThat(result.shortCircuited()).isFalse();
    assertThat(result.blockedRuleCode()).isNull();
    assertThat(result.llmResponse()).isSameAs(response);
    verify(llmGatewayService).invoke(LLM_REQUEST);
  }

  @Test
  void pure_rule_review_without_llm_step() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(ruleQueryService.findByCode("t1", "R01")).thenReturn(Optional.of(rule));
    when(ruleService.evaluate("id-1", Map.of("amount", 15000.0), false))
        .thenReturn(RuleEvalResult.hit("R01", 1, "trace", 5, true));

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest("t1", List.of("R01"), Map.of("amount", 15000.0), null, false));

    assertThat(result.shortCircuited()).isTrue();
    assertThat(result.blockedRuleCode()).isEqualTo("R01");
    assertThat(result.ruleResults()).hasSize(1);
  }

  @Test
  void evaluates_all_active_when_no_codes() {
    when(ruleService.evaluateAll("t1", Map.of("amount", 100.0), false))
        .thenReturn(List.of(RuleEvalResult.miss("R01", 1, "trace", 3)));

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest("t1", List.of(), Map.of("amount", 100.0), null, false));

    assertThat(result.shortCircuited()).isFalse();
    assertThat(result.ruleResults()).hasSize(1);
    verify(ruleService).evaluateAll("t1", Map.of("amount", 100.0), false);
  }
}
