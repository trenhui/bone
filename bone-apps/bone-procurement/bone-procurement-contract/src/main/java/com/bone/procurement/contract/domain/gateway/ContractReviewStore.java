package com.bone.procurement.contract.domain.gateway;

import com.bone.procurement.contract.domain.model.review.ContractReview;
import java.util.Optional;

/** 审查聚合持久化端口（walking skeleton 用受控内存实现，生产持久化端口就绪前不注册生产 bean）。 */
public interface ContractReviewStore {

  void save(String tenantId, ContractReview review);

  /** 按租户隔离查询：跨租户一律返回 empty。 */
  Optional<ContractReview> find(String tenantId, String reviewId);

  /** 全局存在性：用于区分「跨租户拒绝」与「记录不存在」。 */
  boolean existsGlobally(String reviewId);

  /**
   * 幂等投递：同租户内同幂等键只落一条；重复投递返回原 review_id，不覆盖原记录。
   *
   * <p>默认实现委托 {@link #save}（内存实现语义）；持久化适配器覆盖此方法，以 uk(tenant_id, idempotency_key) 去重。保留原有三个方法签名不变。
   */
  default String saveIdempotent(String tenantId, String idempotencyKey, ContractReview review) {
    save(tenantId, review);
    return review.reviewId();
  }
}
