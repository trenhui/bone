package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.contract.domain.gateway.ContractParsePort;
import com.bone.procurement.contract.domain.gateway.ContractReviewPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ParsedContract;
import com.bone.procurement.contract.domain.model.review.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.bone.procurement.contract.infrastructure.InMemoryContractReviewStore;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 合同人工终决身份绑定：匿名拒绝、冒名拒绝、登录态权威值落库。 */
class ContractReviewIdentityBindingTest {

  private final InMemoryContractReviewStore store = new InMemoryContractReviewStore();
  private final List<ReviewTraceEntry> traces = new ArrayList<>();
  private final ReviewTracePort tracePort =
      new ReviewTracePort() {
        @Override
        public void append(ReviewTraceEntry entry) {
          traces.add(entry);
        }

        @Override
        public List<ReviewTraceEntry> findByReviewId(String reviewId) {
          return traces.stream().filter(e -> e.reviewId().equals(reviewId)).toList();
        }
      };
  private final ContractReviewedConsumer consumer = new ContractReviewedConsumer(tracePort);

  @Test
  void 无登录身份时终决被拒且不落终态() {
    ContractReviewApplicationService service = service(null);
    String reviewId = service.submit(command("idem-anon")).reviewId();

    assertThatThrownBy(
            () -> service.override(reviewId, new HumanOverrideInput(null, "理由", RiskLevel.HIGH)))
        .as("匿名/身份不可解析必须 fail-closed")
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.getReport(reviewId).humanFinalized()).isFalse();
  }

  @Test
  void 请求复核人与登录态不一致被拒且不落终态() {
    ContractReviewApplicationService service = service("e2e-admin");
    String reviewId = service.submit(command("idem-spoof")).reviewId();

    assertThatThrownBy(
            () ->
                service.override(
                    reviewId, new HumanOverrideInput("attacker", "冒名", RiskLevel.HIGH)))
        .as("请求体自报复核人不得覆盖登录态")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.getReport(reviewId).humanFinalized()).isFalse();
  }

  @Test
  void 登录态复核人作为权威值落库并留痕() {
    ContractReviewApplicationService service = service("e2e-admin");
    String reviewId = service.submit(command("idem-login")).reviewId();

    ReviewReport report =
        service.override(reviewId, new HumanOverrideInput("", "登录态权威复核", RiskLevel.HIGH));

    assertThat(report.humanFinalized()).isTrue();
    assertThat(report.humanReason()).isEqualTo("登录态权威复核");
    assertThat(traces)
        .anyMatch(
            e ->
                e.eventType().equals("HUMAN_OVERRIDE")
                    && e.detail().contains("reviewer=e2e-admin"));
    assertThat(traces).anyMatch(e -> e.eventType().equals("SNAPSHOT_IMMUTABLE"));
  }

  @Test
  void 空理由或空终决等级被拒且不落终态() {
    ContractReviewApplicationService service = service("e2e-admin");
    String emptyReasonId = service.submit(command("idem-empty-reason")).reviewId();
    String emptyRiskId = service.submit(command("idem-empty-risk")).reviewId();

    assertThatThrownBy(
            () ->
                service.override(emptyReasonId, new HumanOverrideInput(null, "  ", RiskLevel.HIGH)))
        .as("终决理由不得为空")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.override(emptyRiskId, new HumanOverrideInput(null, "理由", null)))
        .as("终决风险等级不得为空")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.getReport(emptyReasonId).humanFinalized()).isFalse();
    assertThat(service.getReport(emptyRiskId).humanFinalized()).isFalse();
  }

  private ContractReviewApplicationService service(String reviewer) {
    ContractParsePort parsePort =
        (input, tenantId, sourceRef, docId) ->
            new ParsedContract(
                docId,
                tenantId,
                sourceRef,
                1,
                List.of(ParsedClause.of("第一条", "甲方应于验收后三十日内付款。", 1, 0, 20)),
                false);
    ContractReviewPort reviewPort =
        request ->
            new ReviewSuggestion(
                AiParticipation.AVAILABLE, RiskLevel.LOW, List.of(), 0.95, false, "m1");
    return new ContractReviewApplicationService(
        parsePort,
        reviewPort,
        tracePort,
        store,
        () -> "t-1",
        () -> reviewer,
        new AiDisclosureService(),
        consumer);
  }

  private static SubmitReviewCommand command(String idempotencyKey) {
    return new SubmitReviewCommand(
        "CT-" + idempotencyKey,
        new ByteArrayInputStream(new byte[0]),
        "src://identity-bing",
        "doc-" + idempotencyKey,
        idempotencyKey);
  }
}
