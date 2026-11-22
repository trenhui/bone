package com.bone.procurement.contract.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ReviewRequest;
import com.bone.procurement.contract.domain.model.review.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock 身份拒绝负向探针：未配置真实 provider 时网关 inject MockLlmClient，其 mock:true 回显 不得被当作模型参与结论进入 AVAILABLE，必须
 * fail-closed 为 UNKNOWN 并留 MOCK_PROVIDER_REJECTED 痕迹。
 */
class ContractReviewMockRejectionTest {

  @Test
  void mock身份响应被拒绝并降级() {
    // 用真实 MockLlmClient 在默认非 JSON 请求下的输出作为回归夹具，避免合成 payload 假绿。
    LlmRequest mockRequest =
        LlmRequest.of(
            "contract-review",
            List.of(com.bone.ai.gateway.domain.LlmMessage.user("请审查以下合同条款")),
            "rv-mock");
    LlmResponse mockResponse = new MockLlmClient().chat(mockRequest);
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any(LlmRequest.class))).thenReturn(mockResponse);
    InMemoryReviewTraceAdapter tracePort = new InMemoryReviewTraceAdapter();
    ContractReviewPortAdapter adapter =
        new ContractReviewPortAdapter(gateway, tracePort, "contract-review");

    ReviewSuggestion suggestion = adapter.review(request());

    assertThat(suggestion.aiAvailable())
        .as("mock 身份不得自报 AVAILABLE，必须 fail-closed 为 UNKNOWN")
        .isEqualTo(AiParticipation.UNKNOWN);
    assertThat(suggestion.riskLevel()).as("不得产生自动结论").isEqualTo(RiskLevel.UNKNOWN);
    assertThat(suggestion.riskItems()).isEmpty();

    List<ReviewTraceEntry> traces = tracePort.findByReviewId("rv-mock");
    assertThat(traces)
        .as("必须留 mock 拒绝痕迹")
        .anyMatch(e -> e.eventType().equals("MOCK_PROVIDER_REJECTED"));
  }

  private static ReviewRequest request() {
    return new ReviewRequest(
        "rv-mock", "CT-1", "t-1", List.of(ParsedClause.of("第一条", "常规保密条款。", 1, 0, 6)));
  }
}
