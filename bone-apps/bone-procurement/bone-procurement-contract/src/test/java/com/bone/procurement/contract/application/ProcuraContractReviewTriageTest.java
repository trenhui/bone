package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.domain.gateway.ContractParsePort;
import com.bone.procurement.contract.domain.gateway.ContractReviewPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractParseFailedException;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ParsedContract;
import com.bone.procurement.contract.domain.model.review.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.bone.procurement.contract.infrastructure.InMemoryContractReviewStore;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 合同审查分诊基线：干净条款 + 高置信 + 低风险时，AI 结论仍只是"待复核建议"，状态停在人工复核、不自动终决。
 *
 * <p>评测符号：baselineGreenScenario_AdvisoryOnlyPendingHuman。
 */
class ProcuraContractReviewTriageTest {

  @Test
  @DisplayName("baselineGreenScenario_AdvisoryOnlyPendingHuman")
  void baselineGreenScenario_AdvisoryOnlyPendingHuman() {
    ParsedClause clause = ParsedClause.of("第一条", "甲方应于验收后三十日内付款。", 1, 0, 20);
    ContractParsePort parsePort =
        (input, tenantId, sourceRef, docId) ->
            new ParsedContract(docId, tenantId, sourceRef, 1, List.of(clause), false);
    ContractReviewPort reviewPort =
        request ->
            new ReviewSuggestion(
                AiParticipation.AVAILABLE, RiskLevel.LOW, List.of(), 0.95, false, "m1");
    List<ReviewTraceEntry> traces = new ArrayList<>();
    ReviewTracePort tracePort =
        new ReviewTracePort() {
          @Override
          public void append(ReviewTraceEntry entry) {
            traces.add(entry);
          }

          @Override
          public List<ReviewTraceEntry> findByReviewId(String reviewId) {
            return List.copyOf(traces);
          }
        };
    ContractReviewApplicationService service =
        new ContractReviewApplicationService(
            parsePort,
            reviewPort,
            tracePort,
            new InMemoryContractReviewStore(),
            () -> "t-1",
            () -> "reviewer-green",
            new AiDisclosureService(),
            new ContractReviewedConsumer(tracePort));

    ReviewReport report =
        service.submit(
            new SubmitReviewCommand(
                "CT-1", new ByteArrayInputStream(new byte[0]), "src://c1", "doc-1", "idem-green"));

    assertThat(report.status()).as("AI 仅辅助，终决前必须停在人工复核").isEqualTo("HUMAN_REVIEW");
    assertThat(report.humanFinalized()).isFalse();
    assertThat(report.aiAvailable()).isEqualTo(AiParticipation.AVAILABLE);
    assertThat(report.riskLevel()).isEqualTo(RiskLevel.LOW);
  }

  @Test
  @DisplayName("材料不全→补件(MANUAL)")
  void 材料不全转人工补件() {
    ContractParsePort parsePort =
        (input, tenantId, sourceRef, docId) -> {
          throw new ContractParseFailedException("材料不全：缺条款定位");
        };
    List<ReviewTraceEntry> traces = new ArrayList<>();
    ContractReviewApplicationService service = service(parsePort, request -> null, traces);

    ReviewReport report =
        service.submit(
            new SubmitReviewCommand(
                "CT-INCOMPLETE",
                new ByteArrayInputStream(new byte[0]),
                "src://incomplete",
                "doc-incomplete",
                "idem-incomplete"));

    assertThat(report.status()).isEqualTo("HUMAN_REVIEW");
    assertThat(report.humanFinalized()).isFalse();
    assertThat(report.humanReason()).contains("材料不全", "人工");
    assertThat(traces).anyMatch(e -> e.eventType().equals("PARSE_FAILED"));
  }

  @Test
  @DisplayName("规则冲突无法裁决→人工")
  void 规则冲突转人工() {
    ContractParsePort parsePort =
        (input, tenantId, sourceRef, docId) ->
            new ParsedContract(
                docId,
                tenantId,
                sourceRef,
                1,
                List.of(ParsedClause.of("第一条", "条款存在冲突表述。", 1, 0, 10)),
                false);
    ContractReviewPort reviewPort =
        request ->
            new ReviewSuggestion(
                AiParticipation.AVAILABLE, RiskLevel.HIGH, List.of(), 0.97, true, "m-conflict");
    ContractReviewApplicationService service = service(parsePort, reviewPort, new ArrayList<>());

    ReviewReport report =
        service.submit(
            new SubmitReviewCommand(
                "CT-CONFLICT",
                new ByteArrayInputStream(new byte[0]),
                "src://conflict",
                "doc-conflict",
                "idem-conflict"));

    assertThat(report.status()).isEqualTo("HUMAN_REVIEW");
    assertThat(report.humanFinalized()).isFalse();
    assertThat(report.humanReason()).contains("HITL 强制人工");
  }

  private static ContractReviewApplicationService service(
      ContractParsePort parsePort, ContractReviewPort reviewPort, List<ReviewTraceEntry> traces) {
    ReviewTracePort tracePort =
        new ReviewTracePort() {
          @Override
          public void append(ReviewTraceEntry entry) {
            traces.add(entry);
          }

          @Override
          public List<ReviewTraceEntry> findByReviewId(String reviewId) {
            return List.copyOf(traces);
          }
        };
    return new ContractReviewApplicationService(
        parsePort,
        reviewPort,
        tracePort,
        new InMemoryContractReviewStore(),
        () -> "t-1",
        () -> "reviewer-triage",
        new AiDisclosureService(),
        new ContractReviewedConsumer(tracePort));
  }
}
