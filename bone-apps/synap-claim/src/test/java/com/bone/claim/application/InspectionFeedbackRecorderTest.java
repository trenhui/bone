package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.claim.application.port.out.AiEvalSamplePort;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;
import com.bone.claim.domain.model.inspection.InspectionAction;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.claim.domain.model.inspection.event.InspectionConcludedEvent;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 学习层：差异按归因回流，PASS 不回流，投递失败不回滚抽检结论。 */
@ExtendWith(MockitoExtension.class)
class InspectionFeedbackRecorderTest {

  @Mock private AiEvalSamplePort aiEvalSamplePort;

  private InspectionFeedbackRecorder recorder() {
    return new InspectionFeedbackRecorder(aiEvalSamplePort);
  }

  @Test
  @DisplayName("AI 误判 → 评测集样本，且带稳定幂等指纹")
  void aiMisjudgeBecomesEvalSample() {
    recorder().onInspectionConcluded(event(InspectionResult.DIFF, InspectionDiffType.AI_MISJUDGE));

    ArgumentCaptor<AiEvalSampleCollectedEvent> captor =
        ArgumentCaptor.forClass(AiEvalSampleCollectedEvent.class);
    verify(aiEvalSamplePort).collect(captor.capture());
    AiEvalSampleCollectedEvent sample = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(sample.sampleType())
        .isEqualTo(AiEvalSampleCollectedEvent.SampleType.EVAL_SET);
    org.assertj.core.api.Assertions.assertThat(sample.fingerprint()).hasSize(16);
    org.assertj.core.api.Assertions.assertThat(sample.claimId()).isEqualTo(1001L);
    org.assertj.core.api.Assertions.assertThat(sample.diffType()).isEqualTo("AI_MISJUDGE");
  }

  @Test
  @DisplayName("理算差异/规则漏命中 → 规则修正候选")
  void ruleMissBecomesRuleFix() {
    recorder().onInspectionConcluded(event(InspectionResult.DIFF, InspectionDiffType.RULE_MISS));

    ArgumentCaptor<AiEvalSampleCollectedEvent> captor =
        ArgumentCaptor.forClass(AiEvalSampleCollectedEvent.class);
    verify(aiEvalSamplePort).collect(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sampleType())
        .isEqualTo(AiEvalSampleCollectedEvent.SampleType.RULE_FIX);
  }

  @Test
  @DisplayName("额度异常 → 控制修正候选")
  void quotaAbnormalBecomesControlFix() {
    recorder()
        .onInspectionConcluded(event(InspectionResult.DIFF, InspectionDiffType.QUOTA_ABNORMAL));

    ArgumentCaptor<AiEvalSampleCollectedEvent> captor =
        ArgumentCaptor.forClass(AiEvalSampleCollectedEvent.class);
    verify(aiEvalSamplePort).collect(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sampleType())
        .isEqualTo(AiEvalSampleCollectedEvent.SampleType.CONTROL_FIX);
  }

  @Test
  @DisplayName("PASS 或无差异：不产生样本（避免把复检一致当误判）")
  void passAndNoneAreIgnored() {
    recorder().onInspectionConcluded(event(InspectionResult.PASS, InspectionDiffType.NONE));
    recorder().onInspectionConcluded(event(InspectionResult.DIFF, InspectionDiffType.NONE));

    verify(aiEvalSamplePort, never()).collect(any());
  }

  @Test
  @DisplayName("同一抽检单的指纹稳定，不同差异类型不会串样")
  void fingerprintIsStableAndScoped() {
    AiEvalSampleCollectedEvent a =
        AiEvalSampleCollectedEvent.from(
            event(InspectionResult.DIFF, InspectionDiffType.AI_MISJUDGE));
    AiEvalSampleCollectedEvent b =
        AiEvalSampleCollectedEvent.from(
            event(InspectionResult.DIFF, InspectionDiffType.AI_MISJUDGE));
    AiEvalSampleCollectedEvent c =
        AiEvalSampleCollectedEvent.from(event(InspectionResult.DIFF, InspectionDiffType.RULE_MISS));

    org.assertj.core.api.Assertions.assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    org.assertj.core.api.Assertions.assertThat(a.fingerprint()).isNotEqualTo(c.fingerprint());
  }

  @Test
  @DisplayName("投递失败只告警，不把异常抛回已提交的抽检事务")
  void dispatchFailureDoesNotBreakConclusion() {
    doThrow(new RuntimeException("事件总线不可用")).when(aiEvalSamplePort).collect(any());

    assertThatCode(
            () ->
                recorder()
                    .onInspectionConcluded(
                        event(InspectionResult.DIFF, InspectionDiffType.AI_MISJUDGE)))
        .doesNotThrowAnyException();
  }

  private static InspectionConcludedEvent event(
      InspectionResult result, InspectionDiffType diffType) {
    return new InspectionConcludedEvent(
        9L,
        42L,
        "QI421001",
        1001L,
        result,
        diffType,
        InspectionAction.REVIEW,
        "",
        "op-1",
        Instant.parse("2026-11-04T10:00:00Z"));
  }
}
