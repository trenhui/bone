package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Disclosure;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Involvement;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 只读披露服务的 IO / 解析护栏：重点是跨案越权与「解析失败不得压成 AI 未参与」。 */
class AiDecisionDisclosureServiceTest {

  private final DecisionExecutionApplicationService executions =
      mock(DecisionExecutionApplicationService.class);
  private final AiDecisionDisclosureService service = new AiDecisionDisclosureService(executions);

  private static DecisionExecution execution(long claimId, String traceId) {
    DecisionExecution e = mock(DecisionExecution.class);
    when(e.getClaimId()).thenReturn(claimId);
    when(e.getTraceId()).thenReturn(traceId);
    when(e.getGraphVersion()).thenReturn("v1");
    when(e.getRuleVersion()).thenReturn("r1");
    when(e.getModelVersion()).thenReturn("");
    when(e.getPromptVersion()).thenReturn("");
    when(e.getStatus()).thenReturn("SUCCESS");
    return e;
  }

  /** 先建桩对象再注册，避免 Mockito 嵌套桩 UnfinishedStubbing。 */
  private static DecisionTraceNode node(String type, int seq, String output, String actionResult) {
    DecisionTraceNode n = mock(DecisionTraceNode.class);
    when(n.getNodeType()).thenReturn(type);
    when(n.getNodeSeq()).thenReturn(seq);
    when(n.getOutput()).thenReturn(output);
    when(n.getActionResult()).thenReturn(actionResult);
    return n;
  }

  @Test
  @DisplayName("无执行记录 → UNDETERMINED，且不回放任何轨迹")
  void noExecutionRecordIsUndetermined() {
    when(executions.findLatest(1001L)).thenReturn(null);

    Disclosure d = service.disclose(1001L, null);

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("NO_EXECUTION_RECORD");
    verify(executions, never()).replay(anyString());
  }

  @Test
  @DisplayName("负向探针：显式 traceId 属于他人案件 → fail-closed 且不回放该轨迹")
  void crossClaimTraceIdIsRejected() {
    DecisionExecution other = execution(9999L, "trace-of-other");
    when(executions.findByTraceId("trace-of-other")).thenReturn(other);

    Disclosure d = service.disclose(1001L, "trace-of-other");

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("TRACE_CLAIM_MISMATCH");
    assertThat(d.traceId()).isEmpty();
    verify(executions, never()).replay(anyString());
  }

  @Test
  @DisplayName("同案显式 traceId → 允许复现历史执行")
  void sameClaimTraceIdIsAllowed() {
    DecisionExecution e = execution(1001L, "trace-1");
    DecisionTraceNode llm = node("LLM", 3, "{\"aiAvailable\":true,\"aiModel\":\"m-1\"}", "OK");
    when(executions.findByTraceId("trace-1")).thenReturn(e);
    when(executions.replay("trace-1")).thenReturn(List.of(llm));

    Disclosure d = service.disclose(1001L, "trace-1");

    assertThat(d.involvement()).isEqualTo(Involvement.AI_ASSISTED);
    assertThat(d.evidence().reportedModelVersion()).isEqualTo("m-1");
    verify(executions).replay("trace-1");
  }

  @Test
  @DisplayName("节点输出带 aiFacts → 显著标识点名生成字段")
  void generatedFieldsAreNamed() {
    DecisionExecution e = execution(1001L, "trace-1");
    DecisionTraceNode llm =
        node(
            "LLM",
            3,
            "{\"aiAvailable\":true,\"aiModel\":\"m-1\",\"aiPromptVersion\":\"p-1\","
                + "\"aiFacts\":[{\"code\":\"X\"}],\"rejectReasons\":[],\"aiDiscrepancies\":[\"d\"]}",
            "OK");
    DecisionTraceNode rule = node("RULE", 2, "{}", "OK");
    when(executions.findLatest(1001L)).thenReturn(e);
    when(executions.replay("trace-1")).thenReturn(List.of(rule, llm));

    Disclosure d = service.disclose(1001L, null);

    assertThat(d.involvement()).isEqualTo(Involvement.AI_ASSISTED);
    assertThat(d.evidence().generatedFields()).containsExactly("aiFacts", "aiDiscrepancies");
    assertThat(d.evidence().traceNodeCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("负向探针：轨迹 JSON 损坏 → UNDETERMINED，不抛异常也不判 AI 未参与")
  void brokenJsonIsUndetermined() {
    DecisionExecution e = execution(1001L, "trace-1");
    DecisionTraceNode llm = node("LLM", 3, "not-a-json", "OK");
    when(executions.findLatest(1001L)).thenReturn(e);
    when(executions.replay("trace-1")).thenReturn(List.of(llm));

    Disclosure d = service.disclose(1001L, null);

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("LLM_OUTPUT_UNPARSABLE");
  }

  @Test
  @DisplayName("负向探针：aiAvailable 键缺失但 model 有值 → 仍 UNDETERMINED")
  void missingFlagWithModelIsUndetermined() {
    DecisionExecution e = execution(1001L, "trace-1");
    DecisionTraceNode llm = node("LLM", 3, "{\"aiModel\":\"m-1\"}", "OK");
    when(executions.findLatest(1001L)).thenReturn(e);
    when(executions.replay("trace-1")).thenReturn(List.of(llm));

    Disclosure d = service.disclose(1001L, null);

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("AI_AVAILABLE_FLAG_MISSING");
  }

  @Test
  @DisplayName("负向探针：轨迹只有规则节点 → UNDETERMINED 而非 AI 未参与")
  void nonLlmTraceIsUndetermined() {
    DecisionExecution e = execution(1001L, "trace-1");
    DecisionTraceNode rule = node("RULE", 2, "{\"fraudGatePassed\":true}", "OK");
    when(executions.findLatest(1001L)).thenReturn(e);
    when(executions.replay("trace-1")).thenReturn(List.of(rule));

    Disclosure d = service.disclose(1001L, null);

    assertThat(d.involvement()).isEqualTo(Involvement.UNDETERMINED);
    assertThat(d.involvementReason()).isEqualTo("LLM_NODE_ABSENT");
  }

  @Test
  @DisplayName("负向探针：节点自报超长版本串被截断，防自由文本外溢披露面")
  void longVersionTextIsClipped() {
    DecisionExecution e = execution(1001L, "trace-1");
    String longModel = "M".repeat(300);
    DecisionTraceNode llm =
        node("LLM", 3, "{\"aiAvailable\":true,\"aiModel\":\"" + longModel + "\"}", "OK");
    when(executions.findLatest(1001L)).thenReturn(e);
    when(executions.replay("trace-1")).thenReturn(List.of(llm));

    Disclosure d = service.disclose(1001L, null);

    assertThat(d.evidence().reportedModelVersion()).hasSize(128);
  }
}
