package com.bone.procurement.contract.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.procurement.contract.domain.model.AiParticipation;
import com.bone.procurement.contract.domain.model.ParsedClause;
import com.bone.procurement.contract.domain.model.ReviewRequest;
import com.bone.procurement.contract.domain.model.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PII 入口拒绝负向探针（D3-v2 §3 / spec「模型 output 原文含身份证号或银行账号被尝试写入报告/事件 → 入口拒绝并触发 PII 告警」）。
 *
 * <p>命中时必须在入域前拒绝：降级 UNKNOWN、不产生自动结论、PII 原文不进建议/事件/报告，并留下 PII_REJECTED 痕迹。
 */
class ProcuraPiiRejectionTest {

  private static final String ID_NO = "110101199003072316";

  @Test
  void 模型输出含身份证号被入口拒绝并降级() {
    String content =
        "{\"riskLevel\":\"HIGH\",\"confidence\":0.99,\"negativeHit\":true,"
            + "\"riskItems\":[{\"clauseNo\":\"第一条\",\"page\":1,\"spanStart\":0,\"spanEnd\":6,"
            + "\"severity\":\"HIGH\",\"basis\":\"甲方身份证号 "
            + ID_NO
            + "\",\"confidence\":0.99}]}";

    verifyRejected(content, ID_NO);
  }

  @Test
  void 模型输出含银行账号被入口拒绝并降级() {
    String account = "6222021234567890123";
    String content =
        "{\"riskLevel\":\"LOW\",\"confidence\":0.9,\"negativeHit\":false,"
            + "\"riskItems\":[{\"clauseNo\":\"第一条\",\"page\":1,\"spanStart\":0,\"spanEnd\":6,"
            + "\"severity\":\"LOW\",\"basis\":\"付款至账号 "
            + account
            + "\",\"confidence\":0.9}]}";

    verifyRejected(content, account);
  }

  private void verifyRejected(String content, String pii) {
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any(LlmRequest.class)))
        .thenReturn(new LlmResponse(content, "m1", "stop", null));
    InMemoryReviewTraceAdapter tracePort = new InMemoryReviewTraceAdapter();
    ContractReviewPortAdapter adapter =
        new ContractReviewPortAdapter(gateway, tracePort, "test-model");

    ReviewSuggestion suggestion = adapter.review(request());

    assertThat(suggestion.aiAvailable())
        .as("PII 命中必须 fail-closed 为 UNKNOWN，不得自报 AVAILABLE")
        .isEqualTo(AiParticipation.UNKNOWN);
    assertThat(suggestion.riskLevel()).as("不得产生自动结论").isEqualTo(RiskLevel.UNKNOWN);
    assertThat(suggestion.riskItems()).isEmpty();
    assertThat(suggestion.toString()).as("PII 原文不得进入领域建议/事件/报告").doesNotContain(pii);

    List<ReviewTraceEntry> traces = tracePort.findByReviewId("rv-pii");
    assertThat(traces).anyMatch(e -> e.eventType().equals("PII_REJECTED"));
    assertThat(traces).allSatisfy(e -> assertThat(e.detail()).doesNotContain(pii));
  }

  private static ReviewRequest request() {
    return new ReviewRequest(
        "rv-pii", "CT-1", "t-1", List.of(ParsedClause.of("第一条", "常规保密条款。", 1, 0, 6)));
  }
}
