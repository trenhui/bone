package com.bone.procurement.contract.application;

import com.bone.procurement.contract.application.ContractReviewedConsumer.ConsumeResult;
import com.bone.procurement.contract.domain.gateway.ContractReviewedEventStore;
import com.bone.procurement.contract.domain.model.review.ContractReviewedDlqEntry;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ContractReviewedOutboxMessage;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 显式投递驱动（本地模拟 MQ listener，不接生产 MQ）：扫描 Outbox → 幂等消费 → 失败入 DLQ → 按 review_id 重放。
 *
 * <p>诚实边界：这是测试/定时任务可调用的显式驱动，不是生产 MQ listener；生产接入仍属 L3。
 */
@Service
@ConditionalOnContractAiReviewEnabled
public class ContractReviewedEventDriver {

  private final ContractReviewedEventStore eventStore;
  private final ContractReviewedConsumer consumer;

  public ContractReviewedEventDriver(
      ContractReviewedEventStore eventStore, ContractReviewedConsumer consumer) {
    this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
    this.consumer = Objects.requireNonNull(consumer, "consumer");
  }

  /** 扫描当前租户的待投递 Outbox；返回非失败投递数。 */
  public int deliverPending(String tenantId) {
    int delivered = 0;
    for (ContractReviewedOutboxMessage message : eventStore.scanPending(tenantId)) {
      if (deliver(message.tenantId(), message.event()) != ConsumeResult.FAILED) {
        delivered++;
      }
    }
    return delivered;
  }

  /** 显式投递单条事件；消费异常写 DLQ 并返回 FAILED，不产生半条结论。 */
  public ConsumeResult deliver(String sourceTenantId, ContractReviewedEvent event) {
    try {
      ConsumeResult result = consumer.consume(sourceTenantId, event);
      if (result != ConsumeResult.FAILED) {
        String consumerTenant = consumer.currentTenant();
        eventStore.markOutboxDelivered(consumerTenant, event.reviewId());
        eventStore
            .findDlq(consumerTenant, event.reviewId())
            .filter(entry -> !entry.resolved())
            .ifPresent(entry -> eventStore.resolveDlq(consumerTenant, event.reviewId()));
      }
      return result;
    } catch (RuntimeException e) {
      String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      String consumerTenant = safeCurrentTenant(sourceTenantId);
      eventStore.appendDlq(
          consumerTenant,
          event.reviewId(),
          reason,
          "outbox://" + consumerTenant + "/" + event.reviewId());
      return ConsumeResult.FAILED;
    }
  }

  /** 按 review_id 重放未解决的 DLQ；已解决或不存在返回 false（重复重放幂等）。 */
  public boolean replay(String tenantId, String reviewId) {
    ContractReviewedDlqEntry entry = eventStore.findDlq(tenantId, reviewId).orElse(null);
    if (entry == null || entry.resolved()) {
      return false;
    }
    ContractReviewedOutboxMessage message = eventStore.findOutbox(tenantId, reviewId).orElse(null);
    if (message == null) {
      return false;
    }
    return deliver(tenantId, message.event()) != ConsumeResult.FAILED;
  }

  private String safeCurrentTenant(String fallback) {
    try {
      return consumer.currentTenant();
    } catch (RuntimeException e) {
      return fallback;
    }
  }
}
