package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.contract.application.ContractReviewedConsumer.ConsumeResult;
import com.bone.procurement.contract.domain.gateway.ContractParsePort;
import com.bone.procurement.contract.domain.gateway.ContractReviewPort;
import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
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
  private final InMemoryContractReviewedEventStore eventStore =
      new InMemoryContractReviewedEventStore();
  private final ContractReviewedConsumer consumer =
      new ContractReviewedConsumer(tracePort, () -> "t-1", eventStore);

  @Test
  void 提交查询推翻披露四用例() {
    AtomicReference<String> tenant = new AtomicReference<>("t-1");
    ContractReviewApplicationService service = service(tenant::get, aiSuggestion(), "reviewer-1");

    ReviewReport submitted = service.submit(command("idem-1", "CT-1"));
    String reviewId = submitted.reviewId();
    assertThat(reviewId).startsWith("rv-");

    ReviewReport report = service.getReport(reviewId);
    assertThat(report.contractNo()).isEqualTo("CT-1");
    assertThat(report.status()).isEqualTo("HUMAN_REVIEW");

    ReviewReport finalized =
        service.override(
            reviewId, new HumanOverrideInput("reviewer-1", "人工判定为高风险", RiskLevel.HIGH));
    assertThat(finalized.status()).isEqualTo("SNAPSHOTTED");
    assertThat(finalized.humanFinalized()).isTrue();
    assertThat(finalized.riskLevel()).isEqualTo(RiskLevel.HIGH);

    AiDisclosureView disclosure = service.disclosure(reviewId);
    assertThat(disclosure.aiAvailableCode()).isEqualTo("AVAILABLE");
    assertThat(disclosure.modelVersionSnapshot()).isEqualTo("m1");
    assertThat(traces).anyMatch(e -> e.eventType().equals("HUMAN_OVERRIDE"));
  }

  @Test
  void 人工推翻后事件被消费一次且重复投递幂等() {
    ContractReviewApplicationService service = service(() -> "t-1", aiSuggestion());
    String reviewId = service.submit(command("idem-e1", "CT-E")).reviewId();
    service.override(reviewId, new HumanOverrideInput("r", "人工推翻", RiskLevel.HIGH));

    // override 只做事务性发布；显式驱动扫描 Outbox 后才落 Inbox 结论。
    new ContractReviewedEventDriver(eventStore, consumer).deliverPending("t-1");
    assertThat(consumer.conclusionCount()).as("override 后审查完成事件经显式驱动只消费一次").isEqualTo(1);
    ContractReviewedConclusion conclusion = consumer.conclusion(reviewId).orElseThrow();
    assertThat(conclusion.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(conclusion.reviewSnapshotRef()).isEqualTo("snap://override/" + reviewId);

    ConsumeResult redelivered =
        consumer.consume(
            new ContractReviewedEvent(
                reviewId,
                "CT-E",
                RiskLevel.HIGH,
                "hash",
                "snap://override/" + reviewId,
                AiParticipation.AVAILABLE,
                "m1"));
    assertThat(redelivered).as("同一 reviewId 重复投递不得再挂结论").isEqualTo(ConsumeResult.DUPLICATE);
    assertThat(consumer.conclusionCount()).isEqualTo(1);
  }

  @Test
  void 跨租户访问拒绝() {
    AtomicReference<String> tenant = new AtomicReference<>("t-1");
    ContractReviewApplicationService service = service(tenant::get, aiSuggestion());
    String reviewId = service.submit(command("idem-t1", "CT-1")).reviewId();

    tenant.set("t-2");
    assertThatThrownBy(() -> service.getReport(reviewId))
        .as("租户不一致必须 fail-closed")
        .isInstanceOf(CrossTenantAccessException.class);
    assertThatThrownBy(
            () -> service.override(reviewId, new HumanOverrideInput("r", "x", RiskLevel.LOW)))
        .isInstanceOf(CrossTenantAccessException.class);
    assertThatThrownBy(() -> service.disclosure(reviewId))
        .isInstanceOf(CrossTenantAccessException.class);
  }

  @Test
  void 无登录租户上下文时提交被拒() {
    ContractReviewApplicationService service = service(() -> null, aiSuggestion());
    assertThatThrownBy(() -> service.submit(command("idem-none", "CT-N")))
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

    ReviewReport report = service.submit(command("idem-deg", "CT-D"));

    assertThat(report.status()).isEqualTo("HUMAN_REVIEW");
    assertThat(report.aiAvailable()).isEqualTo(AiParticipation.UNKNOWN);
    assertThat(traces).anyMatch(e -> e.eventType().equals("AI_DEGRADED"));
  }

  @Test
  void 服务端生成reviewId且请求幂等键去重() {
    ContractReviewApplicationService service = service(() -> "t-1", aiSuggestion());

    ReviewReport first = service.submit(command("idem-dup", "CT-X"));
    ReviewReport second = service.submit(command("idem-dup", "CT-Y"));

    assertThat(first.reviewId()).startsWith("rv-");
    assertThat(second.reviewId()).as("同租户同幂等键重复提交必须返回服务端生成的原 reviewId").isEqualTo(first.reviewId());
  }

  private ContractReviewApplicationService service(
      ContractReviewTenantPort tenantPort, ContractReviewPort reviewPort) {
    return service(tenantPort, reviewPort, "r");
  }

  private ContractReviewApplicationService service(
      ContractReviewTenantPort tenantPort, ContractReviewPort reviewPort, String reviewer) {
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
        parsePort,
        reviewPort,
        tracePort,
        store,
        tenantPort,
        () -> reviewer,
        new AiDisclosureService(),
        new ContractReviewedEventPublisher(store, eventStore));
  }

  private static ContractReviewPort aiSuggestion() {
    return request ->
        new ReviewSuggestion(
            AiParticipation.AVAILABLE, RiskLevel.LOW, List.of(), 0.95, false, "m1");
  }

  private static SubmitReviewCommand command(String idempotencyKey, String contractNo) {
    return new SubmitReviewCommand(
        contractNo,
        new ByteArrayInputStream(new byte[0]),
        "src://c",
        "doc-" + idempotencyKey,
        idempotencyKey);
  }
}
