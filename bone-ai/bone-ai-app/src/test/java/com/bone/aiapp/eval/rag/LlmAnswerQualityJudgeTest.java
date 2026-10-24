package com.bone.aiapp.eval.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aiapp.eval.gateway.JudgeInput;
import com.bone.aiapp.eval.gateway.JudgePort;
import com.bone.aiapp.eval.gateway.JudgeVerdict;
import com.bone.airag.domain.service.AnswerQuality;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link LlmAnswerQualityJudge} 单测（缺口③语义层）：锁死「走网关计量 / fail-closed 解析 / 无据短路 / 精度委派」四条契约。 */
class LlmAnswerQualityJudgeTest {

  private final JudgePort judgePort = mock(JudgePort.class);
  private final LlmAnswerQualityJudge judge = new LlmAnswerQualityJudge(judgePort);

  private static final String C_PENALTY = "逾期付款应支付违约金";
  private static final String C_CONFIDENTIALITY = "保密期限为五年";
  private static final String GROUNDED_ANSWER = "逾期付款应支付违约金。";

  @Test
  @DisplayName("语义评判经 JudgePort 出入（模型调用唯一入站），解析 faithfulness/relevance/unsupported")
  void judges_through_metered_port_and_parses_all_fields() {
    when(judgePort.judge(any()))
        .thenReturn(
            new JudgeVerdict(
                0.9,
                true,
                "依据上下文：{\"faithfulness\":0.9,\"answer_relevance\":0.8,"
                    + "\"unsupported_claims\":[\"与上下文无关的断言\"]}"));

    AnswerQuality q =
        judge.judge("逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY), GROUNDED_ANSWER);

    assertThat(q.faithfulness()).isEqualTo(0.9);
    assertThat(q.answerRelevance()).isEqualTo(0.8);
    assertThat(q.unsupportedClaims()).containsExactly("与上下文无关的断言");

    // 上下文经分隔符合并后作为 expected 传入（判官不得丢上下文）
    ArgumentCaptor<JudgeInput> captor = ArgumentCaptor.forClass(JudgeInput.class);
    verify(judgePort).judge(captor.capture());
    assertThat(captor.getValue().expected()).isEqualTo(C_PENALTY + "\n---\n" + C_CONFIDENTIALITY);
    assertThat(captor.getValue().actual()).isEqualTo(GROUNDED_ANSWER);
    assertThat(captor.getValue().input()).isEqualTo("逾期付款违约金");
  }

  @Test
  @DisplayName("fail-closed：模型输出不可解析记 0 分并带回原因（禁静默放行）")
  void unparseable_output_fails_closed_to_zero() {
    when(judgePort.judge(any())).thenReturn(new JudgeVerdict(0.0, false, "模型今天不想输出 JSON"));

    AnswerQuality q =
        judge.judge("逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY), GROUNDED_ANSWER);

    assertThat(q.faithfulness()).isZero();
    assertThat(q.answerRelevance()).isZero();
    assertThat(q.unsupportedClaims()).anyMatch(c -> c.startsWith("judge_output_unparseable"));
  }

  @Test
  @DisplayName("fail-closed：空响应同样 0 分")
  void empty_response_fails_closed() {
    when(judgePort.judge(any())).thenReturn(new JudgeVerdict(0.0, false, null));

    AnswerQuality q = judge.judge("逾期付款违约金", List.of(C_PENALTY), GROUNDED_ANSWER);

    assertThat(q.faithfulness()).isZero();
  }

  @Test
  @DisplayName("越界分数钳制到 [0,1]（模型输出 1.7 不得冲毁判据不变量）")
  void out_of_range_scores_are_clamped() {
    when(judgePort.judge(any()))
        .thenReturn(
            new JudgeVerdict(
                0.0,
                false,
                "{\"faithfulness\":1.7,\"answer_relevance\":-0.3,\"unsupported_claims\":[]}"));

    AnswerQuality q = judge.judge("逾期付款违约金", List.of(C_PENALTY), GROUNDED_ANSWER);

    assertThat(q.faithfulness()).isEqualTo(1.0);
    assertThat(q.answerRelevance()).isZero();
  }

  @Test
  @DisplayName("无据短路：空上下文不发模型调用，直接 fail-closed（省成本，姿态与确定性层一致）")
  void empty_contexts_short_circuit_without_model_call() {
    AnswerQuality q = judge.judge("逾期付款违约金", List.of(), GROUNDED_ANSWER);

    assertThat(q.faithfulness()).isZero();
    verify(judgePort, never()).judge(any());
  }

  @Test
  @DisplayName("无产出短路：空答案不发模型调用")
  void blank_answer_short_circuits_without_model_call() {
    AnswerQuality q = judge.judge("逾期付款违约金", List.of(C_PENALTY), "  ");

    assertThat(q.faithfulness()).isZero();
    verify(judgePort, never()).judge(any());
  }

  @Test
  @DisplayName("contextPrecision 委派确定性判官：口径与 CI 门禁一致（两层可对账）")
  void context_precision_delegates_to_deterministic_judge() {
    when(judgePort.judge(any()))
        .thenReturn(
            new JudgeVerdict(
                1.0,
                true,
                "{\"faithfulness\":1.0,\"answer_relevance\":1.0,\"unsupported_claims\":[]}"));

    // 2 条上下文，答案只用第 1 条 → 精确度 0.5（确定性口径）
    AnswerQuality q =
        judge.judge("逾期付款违约金", List.of(C_PENALTY, C_CONFIDENTIALITY), GROUNDED_ANSWER);

    assertThat(q.contextPrecision()).isEqualTo(0.5);
    assertThat(q.faithfulness()).isEqualTo(1.0);
  }
}
