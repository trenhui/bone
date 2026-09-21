package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.projection.PaymentProjection;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 支付单仓储（ADR-0030：写侧与本聚合读模型合并为单一仓储）。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>两条全租户扫描走 Criteria 通道（单表过滤即可，无需外置 SQL），显式关闭租户过滤。{@code @TenantScope(ALL)} 由 {@code
 * ArchitectureTest} 门禁识别。
 */
public interface PaymentRepository extends Repository<Payment, Long> {

  /** 超时未关闭的支付单（全租户，定时任务专用）：{@code status ∈ {PENDING, PAYING}} 且 {@code created_at < before}。 */
  @TenantScope(TenantScopeMode.ALL)
  default List<PaymentProjection> findExpiredPaymentsAllTenants(Instant before) {
    Criteria<Payment> criteria =
        Criteria.<Payment>create()
            .disableTenantFilter()
            .in(Payment::getStatus, PaymentStatus.PENDING, PaymentStatus.PAYING)
            .lt(Payment::getCreatedAt, Timestamp.from(before));
    return findByCriteria(criteria).stream().map(PaymentProjection::from).toList();
  }

  /** 对账扫描（全租户）：早于 {@code before} 已 SUCCESS 的支付单，供「钱货不一致」核查。 */
  @TenantScope(TenantScopeMode.ALL)
  default List<PaymentProjection> findSuccessPaymentsBeforeAllTenants(Instant before) {
    Criteria<Payment> criteria =
        Criteria.<Payment>create()
            .disableTenantFilter()
            .eq(Payment::getStatus, PaymentStatus.SUCCESS)
            .lt(Payment::getCreatedAt, Timestamp.from(before));
    return findByCriteria(criteria).stream().map(PaymentProjection::from).toList();
  }
}
