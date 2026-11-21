package com.bone.procurement.contract.infrastructure;

import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.model.review.ContractReview;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 受控内存审查仓储（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated 端口已由 {@link ContractReviewStoreAdapter} 委托 SDK 持久化仓储实现；同接口保留 bean 会双 bean。
 */
@Deprecated
public class InMemoryContractReviewStore implements ContractReviewStore {

  private record Entry(String tenantId, ContractReview review) {}

  private final Map<String, Entry> byReviewId = new ConcurrentHashMap<>();
  private final Map<String, String> reviewIdByIdempotencyKey = new ConcurrentHashMap<>();

  @Override
  public void save(String tenantId, ContractReview review) {
    byReviewId.put(review.reviewId(), new Entry(tenantId, review));
    reviewIdByIdempotencyKey.putIfAbsent(key(tenantId, review.reviewId()), review.reviewId());
  }

  @Override
  public String saveIdempotent(String tenantId, String idempotencyKey, ContractReview review) {
    String existing = reviewIdByIdempotencyKey.get(key(tenantId, idempotencyKey));
    if (existing != null) {
      return existing;
    }
    save(tenantId, review);
    reviewIdByIdempotencyKey.put(key(tenantId, idempotencyKey), review.reviewId());
    return review.reviewId();
  }

  private static String key(String tenantId, String idempotencyKey) {
    return tenantId + "::" + idempotencyKey;
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
