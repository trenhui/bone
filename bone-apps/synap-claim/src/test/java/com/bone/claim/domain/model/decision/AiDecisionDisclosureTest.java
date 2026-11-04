package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.decision.AiDecisionDisclosure.AiNodeFacts;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Disclosure;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Involvement;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AI 参与决策披露的纯函数护栏：重点证明「无法判定」永远不会被压成「AI 未参与」。 */
class AiDecisionDisclosureTest {

  private static DecisionExecution execution(String declaredModel) {
    DecisionExecution e = mock(DecisionExecution.class);
    when(e.getClaimId()).thenReturn(1001L);
    when(e.getTraceId()).thenReturn("trace-1");
    when(e.getGraphVersion()).thenReturn("v1");
    when(e.getRuleVersion()).thenReturn("r1");
    when(e.getModelVersion()).thenReturn(declaredModel);
    when(e.getPromptVersion()).thenReturn("p1");
    when(e.getDecisionResult()).thenReturn("MANUAL_REQUIRED");
    when(e.getTriageReason()).thenReturn("RISK_HIT");
    when(e.getConsumptionReason()).thenReturn("AUTO_APPROVE_DISABLED");
    when(e.getStatus()).thenReturn("SUCCESS");
    return e;
  }

  private static AiNodeFacts assisted(String model, String prompt) {
    return new AiNodeFacts(
        true,
        true,
        false,
        "OK",
        model,
        prompt,
        List.of("aiFacts"),
        List.of("EVIDENCE_INSUFFICIENT"));
  }

  @Test
  @DisplayName("节点自报可用 → AI_ASSISTED，显著标识与辅助角色同时下发")
  void assistedNodeYardsAiAssisted() {
    Disclosure d = AiDecisionDisclosure.of(execution("gpt-x"), List.of(), assisted("m-1", "p-1"));

    assertThat(d.involvement()).isEqualTo(Involvement.AI_ASSISTED);
    assertThat(d.involvementReason()).isEqualTo("LLM_NODE_SUCCEEDED");
    assertThat(d.label()).isEqualTo(AiDecisionDisclosure.LABEL_ASSISTED);
    assertThat(d.aiRole()).isEqualTo("ASSISTIVE_ONLY");
    assertThat(d.rights())
        .containsExactly("REQUEST_HUMAN_REVIEW", "APPEAL_WITHIN_48H", "EXPLANATION_OF_DECISION");
  }

  @Test
  @DisplayName("节点自报降级 → AI_UNAVAILABLE，且不得写成无风险结论")
  void fallbackNodeIsNotNoRisk() {
    AiNodeFacts f =
        new AiNodeFacts(true, false, false, "LLM_UNAVAILABLE", "", "", List.of(), List.of());
    Disclosure d = AiDecisionDisclosure.of(execution(""), List.of(), f);

    assertThat(d.involvement()).isEqualTo(Involvement.AI_UNAVAILABLE);
    assertThat(d.label()).isEqualTo(AiDecisionDisclosure.LABEL_UNAVAILABLE);
    assertThat(d.label()).contains("不得据此认定");
    assertThat(d.aiRole()).isEqualTo("NOT_APPLICABLE");
  }

  @Test
  @DisplayName("负向探针：无 LLM 节点 → UNDETERMINED，绝不等于「AI 未参与」")
  void absentNodeIsUndetermined() {
    Disclosure d = AiDecisionDisclosure.of(execution("gpt-x"), List.of(), AiNodeFacts.absent());

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("LLM_NODE_ABSENT");
    assertThat(d.label()).contains("无法确认");
    assertThat(d.aiRole()).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("负向探针：轨迹 JSON 不可解析 → UNDETERMINED，不是 AI 未参与")
  void unparsableOutputIsUndetermined() {
    Disclosure d = AiDecisionDisclosure.of(execution(""), List.of(), AiNodeFacts.unparsable("OK"));

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("LLM_OUTPUT_UNPARSABLE");
  }

  @Test
  @DisplayName("负向探针：aiAvailable 键缺失 → UNDETERMINED（false 是确认降级，null 是不知道）")
  void missingFlagIsUndetermined() {
    AiNodeFacts f = new AiNodeFacts(true, null, false, "OK", "m-1", "p-1", List.of(), List.of());
    Disclosure d = AiDecisionDisclosure.of(execution("gpt-x"), List.of(), f);

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("AI_AVAILABLE_FLAG_MISSING");
  }

  @Test
  @DisplayName("负向探针：执行行版本快照非空也不能证明模型跑过")
  void modelSnapshotDoesNotProveParticipation() {
    Disclosure d =
        AiDecisionDisclosure.of(
            execution("gpt-4o-filled-by-caller"), List.of(), AiNodeFacts.absent());

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.evidence().declaredModelVersion()).isEqualTo("gpt-4o-filled-by-caller");
    // 两个来源必须分开，不能让快照冒充节点自报
    assertThat(d.evidence().reportedModelVersion()).isEmpty();
  }

  @Test
  @DisplayName("AI 参与但节点未自报版本 → 如实记缺口，不假装可完整复算")
  void assistedWithoutVersionRecordsGap() {
    Disclosure d = AiDecisionDisclosure.of(execution(""), List.of(), assisted("", ""));

    assertThat(d.involvement()).isEqualTo(Involvement.AI_ASSISTED);
    assertThat(d.evidence().traceGaps())
        .contains(
            "TRACE_EMPTY",
            "NODE_MODEL_VERSION_MISSING",
            "NODE_PROMPT_VERSION_MISSING",
            "EXECUTION_MODEL_SNAPSHOT_MISSING");
  }

  @Test
  @DisplayName("负向探针：三种标识与人工最终决策都不得把 AI 表述为决定者")
  void labelNeverClaimsAiDecides() {
    List<String> texts =
        List.of(
            AiDecisionDisclosure.LABEL_ASSISTED,
            AiDecisionDisclosure.LABEL_UNAVAILABLE,
            AiDecisionDisclosure.LABEL_UNDETERMINED,
            AiDecisionDisclosure.HUMAN_FINAL_DECISION,
            AiDecisionDisclosure.SCOPE_NOTE);

    for (String text : texts) {
      assertThat(text).doesNotContain("AI 决定", "AI 最终", "自动拒赔", "由 AI 作出");
    }
    assertThat(AiDecisionDisclosure.HUMAN_FINAL_DECISION).contains("人工核赔人员");
    assertThat(AiDecisionDisclosure.SCOPE_NOTE).contains("放款许可");
  }

  @Test
  @DisplayName("无执行记录工厂：fail-closed 且不回带任何他人证据")
  void undeterminedFactoryIsFailClosed() {
    Disclosure d = AiDecisionDisclosure.undetermined(1001L, "NO_EXECUTION_RECORD");

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.traceId()).isEmpty();
    assertThat(d.evidence().declaredModelVersion()).isEmpty();
    assertThat(d.evidence().traceGaps()).containsExactly("NO_EXECUTION_RECORD");
    assertThat(d.rights()).isUnmodifiable();
  }

  @Test
  @DisplayName("负向探针：证据结构不承载轨迹 output 原文（禁 PII 外溢）")
  void evidenceHasNoRawOutputField() {
    List<String> components =
        Arrays.stream(AiDecisionDisclosure.Evidence.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();

    assertThat(components).doesNotContain("output", "rawOutput", "inputSummary");
    assertThat(components).contains("generatedFields", "traceGaps", "rejectReasons");
  }
}
