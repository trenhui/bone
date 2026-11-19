package com.bone.procurement.contract.infrastructure;

import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.model.ContractReview;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 受控内存审查仓储：walking skeleton 跑通链路用，生产持久化端口就绪后可替换。 */
@Component
@ConditionalOnContractAiReviewEnabled
public class InMemoryContractReviewStore implements ContractReviewStore {

  private record Entry(String tenantId, ContractReview review) {}

  private final Map<String, Entry> byReviewId = new ConcurrentHashMap<>();

  @Override
  public void save(String tenantId, ContractReview review) {
    byReviewId.put(review.reviewId(), new Entry(tenantId, review));
  }

  @Override
  public Optional<ContractReview> find(String tenantId, String reviewId) {
    Entry entry = byReviewId.get(reviewId);
    if (entry != null && entry.tenantId().equals(tenantId)) {
      return Optional.of(entry.review());
    }
    return Optional.empty();
  }

  @Override
  public boolean existsGlobally(String reviewId) {
    return byReviewId.containsKey(reviewId);
  }
}
