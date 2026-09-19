package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.payment.Payment;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Optional;

/**
 * 支付单仓储。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现；约定同 {@link
 * OrderRepository}（加载走租户内条件查询、更新走 SDK 原生 {@code @Version} 乐观锁（{@code update(entity)}），接口内不写 {@code
 * static} / {@code private} 方法）。
 */
public interface PaymentRepository extends Repository<Payment, Long> {

  /**
   * 按 id 加载租户内支付单；id / tenantId 为空、跨租户、已软删时返回 {@code Optional.empty()}（理由见 {@link
   * OrderRepository}）。
   */
  default Optional<Payment> findByIdInTenant(Long id, Long tenantId) {
    if (id == null || tenantId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<Payment>create().eq(Payment::getId, id).eq(Payment::getTenantId, tenantId)));
  }
}
