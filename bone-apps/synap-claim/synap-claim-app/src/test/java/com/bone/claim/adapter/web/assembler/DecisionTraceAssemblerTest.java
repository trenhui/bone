package com.bone.claim.adapter.web.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.controller.DecisionController;
import com.bone.claim.adapter.web.controller.ReviewTaskController;
import com.bone.claim.adapter.web.dto.response.DecisionTraceResp;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecisionTraceAssemblerTest {

  @Test
  @DisplayName("轨迹字段逐项映射且不含租户与基类字段")
  void mapsAllContractFields() {
    DecisionTraceNode node = mock(DecisionTraceNode.class);
    when(node.getTraceId()).thenReturn("trace-1");
    when(node.getClaimId()).thenReturn(22L);
    when(node.getExecutionId()).thenReturn(33L);
    when(node.getNodeSeq()).thenReturn(3);
    when(node.getNodeType()).thenReturn("LLM");
    when(node.getNodeName()).thenReturn("AiAssess");
    when(node.getInputSummary()).thenReturn("摘要");
    when(node.getOutput()).thenReturn("{\"aiAvailable\":true}");
    when(node.getActionResult()).thenReturn("SKIPPED");
    when(node.getDurationMs()).thenReturn(17);

    DecisionTraceResp resp = DecisionTraceAssembler.toResp(List.of(node)).get(0);

    assertThat(resp.traceId()).isEqualTo("trace-1");
    assertThat(resp.claimId()).isEqualTo(22L);
    assertThat(resp.executionId()).isEqualTo(33L);
    assertThat(resp.nodeSeq()).isEqualTo(3);
    assertThat(resp.nodeType()).isEqualTo("LLM");
    assertThat(resp.nodeName()).isEqualTo("AiAssess");
    assertThat(resp.inputSummary()).isEqualTo("摘要");
    assertThat(resp.output()).isEqualTo("{\"aiAvailable\":true}");
    assertThat(resp.actionResult()).isEqualTo("SKIPPED");
    assertThat(resp.durationMs()).isEqualTo(17);
    assertThat(DecisionTraceResp.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly(
            "traceId",
            "claimId",
            "executionId",
            "nodeSeq",
            "nodeType",
            "nodeName",
            "inputSummary",
            "output",
            "actionResult",
            "durationMs");
  }

  @Test
  @DisplayName("null 与空轨迹回落空列表，不抛异常")
  void nullAndEmpty() {
    assertThat(DecisionTraceAssembler.toResp(null)).isEmpty();
    assertThat(DecisionTraceAssembler.toResp(List.of())).isEmpty();
  }

  @Test
  @DisplayName("两个回放端点都不得再直出 DecisionTraceNode（HC-003 负向钉子）")
  void controllersMustNotReturnDomainEntities() throws Exception {
    String decisionReplay =
        DecisionController.class
            .getMethod("replay", long.class, String.class)
            .getGenericReturnType()
            .getTypeName();
    String reviewTrace =
        ReviewTaskController.class
            .getMethod("trace", long.class)
            .getGenericReturnType()
            .getTypeName();

    assertThat(decisionReplay).doesNotContain("DecisionTraceNode");
    assertThat(reviewTrace).doesNotContain("DecisionTraceNode");
    assertThat(decisionReplay).contains("DecisionTraceResp");
    assertThat(reviewTrace).contains("DecisionTraceResp");
  }
}
