package com.bone.aiorch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.aiorch.domain.decision.DecisionVerdict;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 编排 × 确定性闸门：AI 失败不阻断、金额/置信度熔断、正常自动放行。 */
class OrchestrationDecisionGateTest {

  private final RuleApplicationService ruleService = mock(RuleApplicationService.class);
  private final RuleQueryApplicationService ruleQueryService =
      mock(RuleQueryApplicationService.class);
  private final LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
  private final OrchestrationApplicationService service =
      new OrchestrationApplicationService(ruleService, ruleQueryService, gateway);

  private OrchestrationRequest request(BigDecimal amount, Double confidence) {
    when(ruleService.evaluateAll(anyString(), anyMap(), anyBoolean())).thenReturn(List.of());
    LlmRequest llm = LlmRequest.of("m", List.of(LlmMessage.user("请判定本次核赔"))).withTraceId("t-1");
    return new OrchestrationRequest(
        "1", List.of(), java.util.Map.of(), llm, false, amount, confidence);
  }

  @Test
  void ai_failure_does_not_break_business_and_routes_to_human() {
    when(ruleService.evaluateAll(anyString(), anyMap(), anyBoolean())).thenReturn(List.of());
    when(gateway.invoke(any()))
        .thenThrow(new BizException(502, "provider timeout", "AI_GATEWAY_PROVIDER_FAIL"));

    OrchestrationRequest req =
        new OrchestrationRequest(
            "1",
            List.of(),
            java.util.Map.of(),
            LlmRequest.of("m", List.of(LlmMessage.user("q"))).withTraceId("t-2"),
            false,
            new BigDecimal("100"),
            0.95);

    // 架构红线：AI 推理失败不得阻断业务流程
    OrchestrationResult result = service.run(req);
    assertThat(result.aiError()).isNotBlank();
    assertThat(result.llmResponse()).isNull();
    assertThat(result.verdict().requiresHuman()).isTrue();
    assertThat(result.verdict().reasons()).contains(DecisionVerdict.REASON_AI_UNAVAILABLE);
  }

  @Test
  void amount_above_threshold_requires_human() {
    when(ruleService.evaluateAll(anyString(), anyMap(), anyBoolean())).thenReturn(List.of());
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse("ok", "m", "stop", new LlmUsage(1, 1, 2)));

    OrchestrationResult result = service.run(request(new BigDecimal("8000"), 0.95));

    assertThat(result.verdict().requiresHuman()).isTrue();
    assertThat(result.verdict().reasons()).contains(DecisionVerdict.REASON_AMOUNT_HIGH);
    assertThat(result.aiError()).isNull();
  }

  @Test
  void low_confidence_requires_human() {
    when(ruleService.evaluateAll(anyString(), anyMap(), anyBoolean())).thenReturn(List.of());
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse("ok", "m", "stop", new LlmUsage(1, 1, 2)));

    OrchestrationResult result = service.run(request(new BigDecimal("100"), 0.6));

    assertThat(result.verdict().requiresHuman()).isTrue();
    assertThat(result.verdict().reasons()).contains(DecisionVerdict.REASON_CONFIDENCE_LOW);
  }

  @Test
  void high_confidence_small_amount_auto_approves() {
    when(ruleService.evaluateAll(anyString(), anyMap(), anyBoolean())).thenReturn(List.of());
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse("ok", "m", "stop", new LlmUsage(1, 1, 2)));

    OrchestrationResult result = service.run(request(new BigDecimal("100"), 0.95));

    assertThat(result.verdict().autoApproved()).isTrue();
    assertThat(result.verdict().reasons()).isEmpty();
    assertThat(result.llmResponse()).isNotNull();
  }
}
