package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.application.ContractReviewedConsumer.ConsumeResult;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.bone.procurement.contract.infrastructure.InMemoryReviewTraceAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 降级 / 停用 / 推翻消费护栏。
 *
 * <p>评测符号：aiDownDoesNotTurnIntoReject。AI 降级不得变成"自动拒绝/自动无风险"，只能 UNKNOWN 隔离转人工。
 */
class ProcuraAiDegradationTest {

  private final InMemoryReviewTraceAdapter tracePort = new InMemoryReviewTraceAdapter();
  private final InMemoryContractReviewedEventStore eventStore =
      new InMemoryContractReviewedEventStore();
  private final ContractReviewedConsumer consumer =
      new ContractReviewedConsumer(tracePort, () -> "t-1", eventStore);

  @Test
  void aiDownDoesNotTurnIntoReject() {
    ContractReviewedEvent event =
        event("rv-down", AiParticipation.UNKNOWN, RiskLevel.UNKNOWN, "snap://rv-down");

    assertThat(consumer.consume(event)).isEqualTo(ConsumeResult.CONSUMED);
    ContractReviewedConclusion conclusion = consumer.conclusion("rv-down").orElseThrow();

    assertThat(conclusion.riskLevel())
        .as("AI 降级不得被压成 LOW/REJECT 自动结论")
        .isEqualTo(RiskLevel.UNKNOWN);
    assertThat(conclusion.aiAvailable()).isEqualTo(AiParticipation.UNKNOWN);
    assertThat(consumer.isQuarantined("rv-down")).as("UNKNOWN 必须隔离转人工").isTrue();
    assertThat(tracePort.findByReviewId("rv-down"))
        .anyMatch(e -> e.eventType().equals("AI_DEGRADED"));
  }

  @Test
  void 重复投递幂等同一reviewId只挂一条() {
    ContractReviewedEvent event =
        event("rv-idem", AiParticipation.AVAILABLE, RiskLevel.LOW, "snap://rv-idem");

    assertThat(consumer.consume(event)).as("首次投递真正消费").isEqualTo(ConsumeResult.CONSUMED);
    assertThat(consumer.consume(event)).as("重复投递跳过").isEqualTo(ConsumeResult.DUPLICATE);
    assertThat(consumer.conclusionCount()).isEqualTo(1);
    assertThat(tracePort.findByReviewId("rv-idem")).isEmpty();
  }

  @Test
  void unknown风险隔离_low不隔离() {
    consumer.consume(event("rv-u", AiParticipation.AVAILABLE, RiskLevel.UNKNOWN, "snap://rv-u"));
    consumer.consume(event("rv-l", AiParticipation.AVAILABLE, RiskLevel.LOW, "snap://rv-l"));

    assertThat(consumer.isQuarantined("rv-u")).isTrue();
    assertThat(consumer.isQuarantined("rv-l")).isFalse();
    assertThat(tracePort.findByReviewId("rv-u"))
        .anyMatch(e -> e.eventType().equals("RISK_UNKNOWN_QUARANTINE"));
  }

  @Test
  void 人工推翻入追溯链且快照不可变() {
    consumer.consume(
        event(
            "rv-override",
            AiParticipation.AVAILABLE,
            RiskLevel.HIGH,
            "snap://override/rv-override"));

    assertThat(tracePort.findByReviewId("rv-override"))
        .anyMatch(e -> e.eventType().equals("SNAPSHOT_IMMUTABLE"));
    assertThat(consumer.conclusion("rv-override").orElseThrow().reviewSnapshotRef())
        .isEqualTo("snap://override/rv-override");
  }

  @Test
  void 停用与未参与分别留痕() {
    consumer.consume(event("rv-off", AiParticipation.UNAVAILABLE, RiskLevel.LOW, "snap://rv-off"));
    consumer.consume(event("rv-und", AiParticipation.UNDETERMINED, RiskLevel.LOW, "snap://rv-und"));

    List<ReviewTraceEntry> off = tracePort.findByReviewId("rv-off");
    List<ReviewTraceEntry> und = tracePort.findByReviewId("rv-und");
    assertThat(off).anyMatch(e -> e.eventType().equals("AI_DISABLED"));
    assertThat(und).anyMatch(e -> e.eventType().equals("AI_DEGRADED"));
  }

  private static ContractReviewedEvent event(
      String reviewId, AiParticipation ai, RiskLevel risk, String snapshotRef) {
    return new ContractReviewedEvent(
        reviewId, "CT-" + reviewId, risk, "hash-" + reviewId, snapshotRef, ai, "model-v1");
  }
}
