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
}
