package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.application.ContractReviewedConsumer.ConsumeResult;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件闭环语义保持：7 字段锁定 / PII 禁回传由 {@link
 * com.bone.procurement.contract.domain.model.review.ContractReviewedEventTest} 守护；
 * 本类补两条持久化链路语义回归——UNKNOWN 仍隔离转人工、人工推翻快照引用不可变。
 */
class ContractReviewedSemanticPreservationTest {

  private final InMemoryContractReviewedEventStore eventStore =
      new InMemoryContractReviewedEventStore();
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
  private final ContractReviewedConsumer consumer =
      new ContractReviewedConsumer(tracePort, () -> "t-1", eventStore);

  @Test
  @DisplayName("UNKNOWN 持久化链路仍隔离转人工，重启重投不得压成 LOW")
  void unknownRemainsQuarantinedInPersistedInbox() {
    ContractReviewedEvent event =
        new ContractReviewedEvent(
            "rv-unknown",
            "CT-U",
            RiskLevel.UNKNOWN,
            "hash-u",
            "snap://rv-unknown",
            AiParticipation.UNKNOWN,
            "m-u");

    assertThat(consumer.consume(event)).isEqualTo(ConsumeResult.CONSUMED);
    ContractReviewedConclusion persisted = eventStore.findInbox("t-1", "rv-unknown").orElseThrow();
    assertThat(persisted.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
    assertThat(persisted.quarantined()).isTrue();
    assertThat(traces).anyMatch(e -> e.eventType().equals("RISK_UNKNOWN_QUARANTINE"));

    // 模拟重启后的新 consumer 重投：幂等跳过，隔离态与快照引用均不被改写。
    ContractReviewedConsumer restarted =
        new ContractReviewedConsumer(tracePort, () -> "t-1", eventStore);
    assertThat(restarted.consume(event)).isEqualTo(ConsumeResult.DUPLICATE);
    assertThat(eventStore.findInbox("t-1", "rv-unknown").orElseThrow().quarantined()).isTrue();
  }

  @Test
  @DisplayName("人工推翻快照引用持久化且不可变，AI 披露三态逐字保留")
  void humanOverrideSnapshotRefImmutable() {
    ContractReviewedEvent event =
        new ContractReviewedEvent(
            "rv-immutable",
            "CT-I",
            RiskLevel.HIGH,
            "hash-i",
            "snap://override/rv-immutable",
            AiParticipation.UNDETERMINED,
            "m-i");

    assertThat(consumer.consume(event)).isEqualTo(ConsumeResult.CONSUMED);
    ContractReviewedConclusion persisted =
        eventStore.findInbox("t-1", "rv-immutable").orElseThrow();
    assertThat(persisted.reviewSnapshotRef()).isEqualTo("snap://override/rv-immutable");
    assertThat(persisted.aiAvailable().disclosureCode()).isEqualTo("UNDETERMINED");
    assertThat(traces)
        .anyMatch(e -> e.eventType().equals("SNAPSHOT_IMMUTABLE"))
        .anyMatch(e -> e.eventType().equals("AI_DEGRADED"));

    ContractReviewedEvent tampered =
        new ContractReviewedEvent(
            "rv-immutable",
            "CT-I",
            RiskLevel.LOW,
            "hash-changed",
            "snap://changed/rv-immutable",
            AiParticipation.AVAILABLE,
            "m-changed");
    assertThat(new ContractReviewedConsumer(tracePort, () -> "t-1", eventStore).consume(tampered))
        .isEqualTo(ConsumeResult.DUPLICATE);
    assertThat(eventStore.findInbox("t-1", "rv-immutable").orElseThrow().reviewSnapshotRef())
        .isEqualTo("snap://override/rv-immutable");
  }
}
