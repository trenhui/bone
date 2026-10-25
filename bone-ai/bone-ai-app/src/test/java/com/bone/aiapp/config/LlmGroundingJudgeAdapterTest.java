package com.bone.aiapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.airag.domain.model.GroundingJudgeVerdict;
import com.bone.airag.domain.model.RagResult;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link LlmGroundingJudgeAdapter} 单测（R5 运行时语义裁判）：锁死「走网关卡面 / 三态判定 / 故障回落不可用（不冒充拒答）」三条契约。 */
class LlmGroundingJudgeAdapterTest {

  private final LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
  private final BoneAiProperties properties = mock(BoneAiProperties.class);
  private final LlmGroundingJudgeAdapter adapter =
      new LlmGroundingJudgeAdapter(gateway, properties);

  private static final List<RagResult> CONTEXTS =
      List.of(new RagResult("chunk-1", "clause-1", 0, 10, 0.9, "第一章", "逾期付款应支付违约金，按日万分之五计算。"));

  private void gatewayReturns(String content) {
    when(properties.getRagAnswerModel()).thenReturn("gpt-4o-mini");
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse(content, "gpt-4o-mini", "stop", new LlmUsage(1, 2, 3)));
  }

  @Test
  @DisplayName("裁判判通过：supported=true，非拒否、非不可用")
  void supported_verdict_passes() {
    gatewayReturns("{\"supported\": true, \"reason\": \"均有支撑\", \"unsupported_sentences\": []}");

    GroundingJudgeVerdict verdict = adapter.judge("违约金怎么算", "按日万分之五。", CONTEXTS);

    assertThat(verdict.supported()).isTrue();
    assertThat(verdict.reason()).isEqualTo(GroundingJudgeVerdict.REASON_SUPPORTED);
    assertThat(verdict.rejected()).isFalse();
    assertThat(verdict.unavailable()).isFalse();
  }

  @Test
  @DisplayName("裁判判无支撑：rejected=true，且带回无支撑句明细（供审计/回归定位）")
  void unsupported_verdict_rejects_with_sentences() {
    gatewayReturns(
        "{\"supported\": false, \"reason\": \"结论上下文未给出\", "
            + "\"unsupported_sentences\": [\"逾期还要坐牢\"]}");

    GroundingJudgeVerdict verdict = adapter.judge("违约金怎么算", "逾期还要坐牢。", CONTEXTS);

    assertThat(verdict.rejected()).isTrue();
    assertThat(verdict.reason()).isEqualTo(GroundingJudgeVerdict.REASON_UNSUPPORTED);
    assertThat(verdict.unsupportedSentences()).contains("逾期还要坐牢");
  }

  @Test
  @DisplayName("判无支撑但无明细：把裁判自述 reason 落进审计轨迹位（不丢现场）")
  void unsupported_without_sentences_keeps_reason() {
    gatewayReturns(
        "{\"supported\": false, \"reason\": \"数字与上下文不一致\", \"unsupported_sentences\": []}");

    GroundingJudgeVerdict verdict = adapter.judge("违约金怎么算", "按日千分之一。", CONTEXTS);

    assertThat(verdict.rejected()).isTrue();
    assertThat(verdict.unsupportedSentences()).anyMatch(s -> s.contains("数字与上下文不一致"));
  }

  @Test
  @DisplayName("容错围栏/散文：模型带散文或 ```json 围栏仍能抽出 JSON 解析")
  void tolerates_prose_and_fence() {
    gatewayReturns(
        "```json\n{\"supported\": true, \"reason\": \"ok\", \"unsupported_sentences\": []}\n```");

    GroundingJudgeVerdict verdict = adapter.judge("q", "a", CONTEXTS);

    assertThat(verdict.supported()).isTrue();
  }

  @Test
  @DisplayName("网关治理拒绝/provider 故障（BizException）：回落不可用，不冒充拒答（rejected=false）")
  void governance_rejection_converges_to_unavailable_not_refusal() {
    when(properties.getRagAnswerModel()).thenReturn("gpt-4o-mini");
    when(gateway.invoke(any()))
        .thenThrow(new BizException(400, "注入疑似", "AI_GATEWAY_INJECTION_SUSPECTED"));

    GroundingJudgeVerdict verdict = adapter.judge("q", "a", CONTEXTS);

    assertThat(verdict.unavailable()).isTrue();
    assertThat(verdict.reason()).isEqualTo(GroundingJudgeVerdict.REASON_UNAVAILABLE);
    // 关键：不得把可用性故障当成合规拒答
    assertThat(verdict.rejected()).isFalse();
  }

  @Test
  @DisplayName("未知运行时故障：同样回落不可用，不抛出（裁判故障不得打断回答链）")
  void unknown_failure_falls_back_to_unavailable() {
    when(properties.getRagAnswerModel()).thenReturn("gpt-4o-mini");
    when(gateway.invoke(any())).thenThrow(new IllegalStateException("boom"));

    GroundingJudgeVerdict verdict = adapter.judge("q", "a", CONTEXTS);

    assertThat(verdict.unavailable()).isTrue();
  }

  @Test
  @DisplayName("无 JSON 输出：不可用（不猜着放行，也不冒充判定）")
  void non_json_output_is_unavailable() {
    gatewayReturns("我不知道");

    GroundingJudgeVerdict verdict = adapter.judge("q", "a", CONTEXTS);

    assertThat(verdict.unavailable()).isTrue();
    assertThat(verdict.rejected()).isFalse();
  }

  @Test
  @DisplayName("JSON 缺 supported 字段：不可用（严校验，不猜）")
  void missing_supported_field_is_unavailable() {
    gatewayReturns("{\"reason\": \"呃\", \"unsupported_sentences\": []}");

    GroundingJudgeVerdict verdict = adapter.judge("q", "a", CONTEXTS);

    assertThat(verdict.unavailable()).isTrue();
  }
}
