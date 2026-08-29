package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.payment.Payment;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 支付单写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>多租户隔离下沉到仓储查询层：{@link #findByIdInTenant} 用 SDK Criteria 附加租户条件（与 {@code
 * OrderRepository.findByIdInTenant} 同模式）。
 */
public interface PaymentRepository extends Repository<Payment, Long> {

  /**
   * 按 id 加载当前租户可访问的支付单。
   *
   * <p>仅返回 {@code id} 与 {@code tenantId} 同时匹配的支付单；跨租户或不存在时返回 {@code null}。
   */
  default Payment findByIdInTenant(Long id, Long tenantId) {
    return findOneByCriteria(
        Criteria.<Payment>create()
            .entityClass(Payment.class)
            .eq("id", id)
            .eq("tenantId", tenantId));
  }
}
