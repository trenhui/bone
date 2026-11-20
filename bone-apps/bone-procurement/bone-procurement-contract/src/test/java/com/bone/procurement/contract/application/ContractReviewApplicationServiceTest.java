package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.contract.domain.gateway.ContractParsePort;
import com.bone.procurement.contract.domain.gateway.ContractReviewPort;
import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.CrossTenantAccessException;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ParsedContract;
import com.bone.procurement.contract.domain.model.review.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.bone.procurement.contract.infrastructure.InMemoryContractReviewStore;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 应用服务四用例 + 跨租户 fail-closed。 */
class ContractReviewApplicationServiceTest {

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
  void 提交查询推翻披露四用例() {
    AtomicReference<String> tenant = new AtomicReference<>("t-1");
    ContractReviewApplicationService service = service(tenant::get, aiSuggestion());

    ReviewReport submitted = service.submit(command("rv-1", "CT-1"));
    assertThat(submitted.reviewId()).isEqualTo("rv-1");

    ReviewReport report = service.getReport("rv-1");
    assertThat(report.contractNo()).isEqualTo("CT-1");
    assertThat(report.status()).isEqualTo("HUMAN_REVIEW");

    ReviewReport finalized =
        service.override("rv-1", new HumanOverrideInput("reviewer-1", "人工判定为高风险", RiskLevel.HIGH));
    assertThat(finalized.status()).isEqualTo("SNAPSHOTTED");
    assertThat(finalized.humanFinalized()).isTrue();
    assertThat(finalized.riskLevel()).isEqualTo(RiskLevel.HIGH);

    AiDisclosureView disclosure = service.disclosure("rv-1");
    assertThat(disclosure.aiAvailableCode()).isEqualTo("AVAILABLE");
    assertThat(disclosure.modelVersionSnapshot()).isEqualTo("m1");
    assertThat(traces).anyMatch(e -> e.eventType().equals("HUMAN_OVERRIDE"));
  }

  @Test
  void 人工推翻后事件被消费一次且重复投递幂等() {
    ContractReviewApplicationService service = service(() -> "t-1", aiSuggestion());
    service.submit(command("rv-e1", "CT-E"));
    service.override("rv-e1", new HumanOverrideInput("r", "人工推翻", RiskLevel.HIGH));

    assertThat(consumer.conclusionCount())
        .as("override 后审查完成事件应在进程内被消费一次（walking skeleton 发布-消费闭环）")
        .isEqualTo(1);
    ContractReviewedConsumer.StoredConclusion conclusion =
        consumer.conclusion("rv-e1").orElseThrow();
    assertThat(conclusion.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(conclusion.reviewSnapshotRef()).isEqualTo("snap://override/rv-e1");

    boolean redelivered =
        consumer.consume(
            new ContractReviewedEvent(
                "rv-e1",
                "CT-E",
                RiskLevel.HIGH,
                "hash",
                "snap://override/rv-e1",
                AiParticipation.AVAILABLE,
                "m1"));
    assertThat(redelivered).as("同一 reviewId 重复投递不得再挂结论").isFalse();
    assertThat(consumer.conclusionCount()).isEqualTo(1);
  }

  @Test
  void 跨租户访问拒绝() {
    AtomicReference<String> tenant = new AtomicReference<>("t-1");
    ContractReviewApplicationService service = service(tenant::get, aiSuggestion());
    service.submit(command("rv-t1", "CT-1"));

    tenant.set("t-2");
    assertThatThrownBy(() -> service.getReport("rv-t1"))
        .as("租户不一致必须 fail-closed")
        .isInstanceOf(CrossTenantAccessException.class);
    assertThatThrownBy(
            () -> service.override("rv-t1", new HumanOverrideInput("r", "x", RiskLevel.LOW)))
        .isInstanceOf(CrossTenantAccessException.class);
    assertThatThrownBy(() -> service.disclosure("rv-t1"))
        .isInstanceOf(CrossTenantAccessException.class);
  }

  @Test
  void 无登录租户上下文时提交被拒() {
    ContractReviewApplicationService service = service(() -> null, aiSuggestion());
    assertThatThrownBy(() -> service.submit(command("rv-none", "CT-N")))
        .as("租户从登录态取，缺失即拒绝（不接受自报）")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 降级建议也进入人工复核并留痕() {
    ContractReviewPort degraded =
        request ->
            new ReviewSuggestion(
                AiParticipation.UNKNOWN, RiskLevel.UNKNOWN, List.of(), 0.0, false, null);
    ContractReviewApplicationService service = service(() -> "t-1", degraded);

    ReviewReport report = service.submit(command("rv-deg", "CT-D"));

    assertThat(report.status()).isEqualTo("HUMAN_REVIEW");
    assertThat(report.aiAvailable()).isEqualTo(AiParticipation.UNKNOWN);
    assertThat(traces).anyMatch(e -> e.eventType().equals("AI_DEGRADED"));
  }

  private ContractReviewApplicationService service(
      ContractReviewTenantPort tenantPort, ContractReviewPort reviewPort) {
    ContractParsePort parsePort =
        (input, tenantId, sourceRef, docId) ->
            new ParsedContract(
                docId,
                tenantId,
                sourceRef,
                1,
                List.of(ParsedClause.of("第一条", "条款正文。", 1, 0, 4)),
                false);
    return new ContractReviewApplicationService(
        parsePort, reviewPort, tracePort, store, tenantPort, new AiDisclosureService(), consumer);
  }

  private static ContractReviewPort aiSuggestion() {
    return request ->
        new ReviewSuggestion(
            AiParticipation.AVAILABLE, RiskLevel.LOW, List.of(), 0.95, false, "m1");
  }

  private static SubmitReviewCommand command(String reviewId, String contractNo) {
    return new SubmitReviewCommand(
        reviewId, contractNo, new ByteArrayInputStream(new byte[0]), "src://c", "doc");
  }
}
