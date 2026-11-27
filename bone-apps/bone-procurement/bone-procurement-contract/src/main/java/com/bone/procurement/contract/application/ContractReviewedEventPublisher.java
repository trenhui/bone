package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.gateway.ContractReviewedEventStore;
import com.bone.procurement.contract.domain.model.review.ContractReview;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务性发布用例：审查结论落库与 Outbox 插入同一本地事务。
 *
 * <p>结论落库失败时不留在途 Outbox（不会"发布成功但无结论"）；本实现不接生产 MQ，待投递消息由显式驱动扫描。
 */
@Service
@ConditionalOnContractAiReviewEnabled
public class ContractReviewedEventPublisher {

  private final ContractReviewStore store;
  private final ContractReviewedEventStore eventStore;

  public ContractReviewedEventPublisher(
      ContractReviewStore store, ContractReviewedEventStore eventStore) {
    this.store = Objects.requireNonNull(store, "store");
    this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
  }

  @Transactional
  public void publishWithConclusion(
      String tenantId, ContractReview review, ContractReviewedEvent event) {
    store.save(tenantId, review);
    eventStore.publishOutbox(tenantId, event);
  }
}
