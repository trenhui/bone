package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.bone.procurement.contract.application.ContractReviewedConsumer.ConsumeResult;
import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReview;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ContractReviewed 事件闭环三类负向探针 + 事务一致性：重复投递只消费一次、跨租户拒绝留痕、失败入 DLQ 可按 review_id 重放。
 *
 * <p>全程本地持久化内存实现 + 显式驱动，不接生产 MQ、不执行生产库。
 */
class ContractReviewedEventChainTest {

  private final InMemoryContractReviewedEventStore eventStore =
      new InMemoryContractReviewedEventStore();
  private final List<ReviewTraceEntry> traces = new ArrayList<>();
  private final ReviewTracePort tracePort = traceSink(traces);

  @Test
  @DisplayName("2.1 事务发布：先落库再入 Outbox，结论落库失败则无孤立 Outbox")
  void failedConclusionLeavesNoOutbox() {
    ContractReviewStore failingStore = mock(ContractReviewStore.class);
    doThrow(new IllegalStateException("存储不可用"))
        .when(failingStore)
        .save(anyString(), any(ContractReview.class));
    ContractReviewedEventPublisher publisher =
        new ContractReviewedEventPublisher(failingStore, eventStore);

    assertThatThrownBy(
            () ->
                publisher.publishWithConclusion(
                    "t-1", ContractReview.pending("rv-fail", "CT-F"), event("rv-fail")))
        .hasMessageContaining("存储不可用");
    assertThat(eventStore.scanPending("t-1")).isEmpty();
  }

  @Test
  @DisplayName("2.1 发布后重启仍可投递：新驱动扫描 Outbox 恢复结论")
  void publishedEventSurvivesRestart() {
    ContractReviewStore store = mock(ContractReviewStore.class);
    new ContractReviewedEventPublisher(store, eventStore)
        .publishWithConclusion("t-1", ContractReview.pending("rv-pub", "CT-P"), event("rv-pub"));
    assertThat(eventStore.scanPending("t-1")).hasSize(1);

    // 模拟进程重启：全新的 consumer / driver，共享同一持久化事件存储。
    ContractReviewedConsumer restartedConsumer = consumer(tracePort);
    ContractReviewedEventDriver restartedDriver =
        new ContractReviewedEventDriver(eventStore, restartedConsumer);
    int delivered = restartedDriver.deliverPending("t-1");

    assertThat(delivered).isEqualTo(1);
    assertThat(restartedConsumer.conclusion("rv-pub")).isPresent();
    assertThat(eventStore.findOutbox("t-1", "rv-pub").orElseThrow().delivered()).isTrue();
  }

  @Test
  @DisplayName("2.2 重复投递只消费一次：第二次返回 DUPLICATE，无第二条结论/追溯")
  void duplicateDeliveryConsumedOnce() {
    ContractReviewedEventDriver driver =
        new ContractReviewedEventDriver(eventStore, consumer(tracePort));
    ContractReviewedEvent e = event("rv-dup");

    assertThat(driver.deliver("t-1", e)).isEqualTo(ConsumeResult.CONSUMED);
    assertThat(driver.deliver("t-1", e)).isEqualTo(ConsumeResult.DUPLICATE);

    traces.clear();
    assertThat(eventStore.inboxCount("t-1")).isEqualTo(1);
    ContractReviewedEventDriver restarted =
        new ContractReviewedEventDriver(eventStore, consumer(tracePort));
    assertThat(restarted.deliver("t-1", e)).isEqualTo(ConsumeResult.DUPLICATE);
    assertThat(traces).as("重复投递不得追加第二条追溯记录").isEmpty();
  }

  @Test
  @DisplayName("2.3 跨租户错投拒绝且留痕：不落库，留痕含 review_id 与原因")
  void crossTenantRejectedWithTrace() {
    ContractReviewedEventDriver driver =
        new ContractReviewedEventDriver(eventStore, consumer(tracePort));

    ConsumeResult result = driver.deliver("t-2", event("rv-cross"));

    assertThat(result).isEqualTo(ConsumeResult.REJECTED);
    assertThat(eventStore.findInbox("t-1", "rv-cross")).isEmpty();
    assertThat(eventStore.findInbox("t-2", "rv-cross")).isEmpty();
    assertThat(traces)
        .anySatisfy(
            t -> {
              assertThat(t.eventType()).isEqualTo("CROSS_TENANT_REJECTED");
              assertThat(t.reviewId()).isEqualTo("rv-cross");
              assertThat(t.detail()).contains("rv-cross").contains("t-2").contains("t-1");
            });
  }

  @Test
  @DisplayName("2.4 消费失败入 DLQ 且不留半条结论")
  void failedConsumptionGoesToDlq() {
    ReviewTracePort failing =
        new ReviewTracePort() {
          @Override
          public void append(ReviewTraceEntry entry) {
            throw new IllegalStateException("追溯写入异常");
          }

          @Override
          public List<ReviewTraceEntry> findByReviewId(String reviewId) {
            return List.of();
          }
        };
    ContractReviewedEventDriver failingDriver =
        new ContractReviewedEventDriver(eventStore, consumer(failing));

    ConsumeResult result = failingDriver.deliver("t-1", event("rv-dlq"));

    assertThat(result).isEqualTo(ConsumeResult.FAILED);
    assertThat(eventStore.findInbox("t-1", "rv-dlq")).isEmpty();
    assertThat(eventStore.findDlq("t-1", "rv-dlq"))
        .isPresent()
        .get()
        .satisfies(
            e -> {
              assertThat(e.reason()).contains("追溯写入异常");
              assertThat(e.resolved()).isFalse();
              assertThat(e.outboxRef()).contains("rv-dlq");
            });
  }

  @Test
  @DisplayName("2.4 按 review_id 重放成功，重复重放不重复产生结论")
  void replaySucceedsAndIsIdempotent() {
    ReviewTracePort failing =
        new ReviewTracePort() {
          @Override
          public void append(ReviewTraceEntry entry) {
            throw new IllegalStateException("追溯写入异常");
          }

          @Override
          public List<ReviewTraceEntry> findByReviewId(String reviewId) {
            return List.of();
          }
        };
    ContractReviewedEvent replayed = event("rv-replay");
    eventStore.publishOutbox("t-1", replayed);
    new ContractReviewedEventDriver(eventStore, consumer(failing)).deliver("t-1", replayed);
    ContractReviewedEventDriver healthy =
        new ContractReviewedEventDriver(eventStore, consumer(tracePort));

    assertThat(healthy.replay("t-1", "rv-replay")).isTrue();
    assertThat(eventStore.findInbox("t-1", "rv-replay")).isPresent();
    assertThat(eventStore.findDlq("t-1", "rv-replay").orElseThrow().resolved()).isTrue();

    int before = eventStore.inboxCount("t-1");
    assertThat(healthy.replay("t-1", "rv-replay")).as("已解决 DLQ 不得重复重放").isFalse();
    assertThat(eventStore.inboxCount("t-1")).isEqualTo(before);
  }

  private static ReviewTracePort traceSink(List<ReviewTraceEntry> traces) {
    return new ReviewTracePort() {
      @Override
      public void append(ReviewTraceEntry entry) {
        traces.add(entry);
      }

      @Override
      public List<ReviewTraceEntry> findByReviewId(String reviewId) {
        return traces.stream().filter(e -> e.reviewId().equals(reviewId)).toList();
      }
    };
  }

  private ContractReviewedConsumer consumer(ReviewTracePort tracePort) {
    return new ContractReviewedConsumer(tracePort, () -> "t-1", eventStore);
  }

  private static ContractReviewedEvent event(String reviewId) {
    return new ContractReviewedEvent(
        reviewId,
        "CT-" + reviewId,
        RiskLevel.HIGH,
        "hash",
        "snap://override/" + reviewId,
        AiParticipation.AVAILABLE,
        "m1");
  }
}
