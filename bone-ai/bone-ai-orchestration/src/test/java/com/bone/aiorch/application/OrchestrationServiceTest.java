package com.bone.aiorch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleEvalResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** OrchestrationApplicationService：规则预检 → 短路 → LLM 的最小流水线语义 + 全链 traceId 贯穿。 */
class OrchestrationServiceTest {

  private RuleApplicationService ruleService;
  private RuleQueryApplicationService ruleQueryService;
  private LlmGatewayApplicationService llmGatewayService;
  private OrchestrationApplicationService service;

  private static final LlmRequest LLM_REQUEST =
      LlmRequest.of("qwen3.5", List.of(new LlmMessage("user", "审查")));

  @BeforeEach
  void setUp() {
    ruleService = mock(RuleApplicationService.class);
    ruleQueryService = mock(RuleQueryApplicationService.class);
    llmGatewayService = mock(LlmGatewayApplicationService.class);
    service = new OrchestrationApplicationService(ruleService, ruleQueryService, llmGatewayService);
  }

  @Test
  void short_circuits_when_rule_hit_with_block() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(ruleQueryService.findByCode("t1", "R01")).thenReturn(Optional.of(rule));
    when(ruleService.evaluate(eq("id-1"), any(Map.class), eq(true)))
        .thenReturn(RuleEvalResult.hit("R01", 1, "trace", 5, true));

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest(
                "t1", List.of("R01"), Map.of("amount", 15000.0), LLM_REQUEST, true, "trace-x"));

    assertThat(result.shortCircuited()).isTrue();
    assertThat(result.blockedRuleCode()).isEqualTo("R01");
    assertThat(result.llmResponse()).isNull();
    verify(llmGatewayService, never()).invoke(any());
  }

  @Test
  void invokes_llm_when_no_rule_hit() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(ruleQueryService.findByCode("t1", "R01")).thenReturn(Optional.of(rule));
    when(ruleService.evaluate(eq("id-1"), any(Map.class), eq(true)))
        .thenReturn(RuleEvalResult.miss("R01", 1, "trace", 4));
    LlmResponse response = new LlmResponse("审查通过", "qwen3.5", "stop", new LlmUsage(10, 5, 15));
    when(llmGatewayService.invoke(any())).thenReturn(response);

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest(
                "t1", List.of("R01"), Map.of("amount", 500.0), LLM_REQUEST, true, "trace-y"));

    assertThat(result.shortCircuited()).isFalse();
    assertThat(result.blockedRuleCode()).isNull();
    assertThat(result.llmResponse()).isSameAs(response);
    // traceId 贯穿：LLM 请求必须绑定编排入口的全链 ID（证据快照才拼得回一次业务动作）
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmGatewayService).invoke(captor.capture());
    assertThat(captor.getValue().traceId()).isEqualTo("trace-y");
  }

  @Test
  void rule_context_receives_trace_id_for_stable_gray_bucketing() {
    when(ruleService.evaluateAll(eq("t1"), any(Map.class), eq(false)))
        .thenReturn(List.of(RuleEvalResult.miss("R01", 1, "trace", 3)));

    service.run(
        new OrchestrationRequest("t1", List.of(), Map.of("amount", 100.0), null, false, "trace-z"));

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(ruleService).evaluateAll(eq("t1"), captor.capture(), eq(false));
    assertThat(captor.getValue()).containsEntry("traceId", "trace-z");
  }

  @Test
  void pure_rule_review_without_llm_step() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(ruleQueryService.findByCode("t1", "R01")).thenReturn(Optional.of(rule));
    when(ruleService.evaluate(eq("id-1"), any(Map.class), eq(false)))
        .thenReturn(RuleEvalResult.hit("R01", 1, "trace", 5, true));

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest(
                "t1", List.of("R01"), Map.of("amount", 15000.0), null, false, "trace-w"));

    assertThat(result.shortCircuited()).isTrue();
    assertThat(result.blockedRuleCode()).isEqualTo("R01");
    assertThat(result.ruleResults()).hasSize(1);
  }

  @Test
  void evaluates_all_active_when_no_codes() {
    when(ruleService.evaluateAll(eq("t1"), any(Map.class), eq(false)))
        .thenReturn(List.of(RuleEvalResult.miss("R01", 1, "trace", 3)));

    OrchestrationResult result =
        service.run(
            new OrchestrationRequest("t1", List.of(), Map.of("amount", 100.0), null, false));

    assertThat(result.shortCircuited()).isFalse();
    assertThat(result.ruleResults()).hasSize(1);
    verify(ruleService).evaluateAll(eq("t1"), any(Map.class), eq(false));
  }
}
