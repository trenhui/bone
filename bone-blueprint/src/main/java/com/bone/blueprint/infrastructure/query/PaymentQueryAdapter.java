package com.bone.blueprint.infrastructure.query;

import com.bone.blueprint.application.query.port.PaymentQueryPort;
import com.bone.blueprint.application.query.projection.PaymentProjection;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 支付读侧适配器：{@link PaymentQueryPort} 的基础设施实现（E-10.3 {@code infrastructure/query}）。
 *
 * <p><b>为何改用 SDK {@link Criteria} 而非手写 JDBC</b>：
 *
 * <ol>
 *   <li>软删由 SDK 自动追加 {@code deleted = 0}，不再依赖每条 SQL 记得写；
 *   <li>跨租户从「SQL 里恰好没写 {@code tenant_id}」的隐式语义，变成 {@link Criteria#disableTenantFilter()} 的显式逃生舱——会打
 *       WARN，可在审计侧检索；
 *   <li>与写侧共用 {@code SqlExecutor}，因此过 {@code SqlSecurityGuard} 与模板安全校验。
 * </ol>
 *
 * <p><b>为何仍保留本端口</b>：调用方是 {@code adapter/schedule} 的定时任务，不得直连 domain 仓储； 且跨租户需要 {@link Criteria}
 * 这类读侧 DSL，它只允许出现在 query 层。
 */
@Component
@RequiredArgsConstructor
public class PaymentQueryAdapter implements PaymentQueryPort {

  private final PaymentRepository paymentRepository;

  @Override
  public List<PaymentProjection> findPayableExpiredBeforeAllTenants(Instant before) {
    // 全租户运维扫描：显式关闭租户过滤（ADR-0029 逃生舱，打 WARN），调用方须为已登记的定时任务
    Criteria<Payment> criteria =
        Criteria.<Payment>create()
            .disableTenantFilter()
            .in(Payment::getStatus, PaymentStatus.PENDING, PaymentStatus.PAYING)
            .lt(Payment::getCreatedAt, Timestamp.from(before));
    return paymentRepository.findByCriteria(criteria).stream()
        .map(PaymentQueryAdapter::toProjection)
        .toList();
  }

  @Override
  public List<PaymentProjection> findSuccessCreatedBeforeAllTenants(Instant before) {
    // 「钱货不一致」对账扫描：已成功的支付单，供调用方核对订单是否已确认支付
    Criteria<Payment> criteria =
        Criteria.<Payment>create()
            .disableTenantFilter()
            .eq(Payment::getStatus, PaymentStatus.SUCCESS)
            .lt(Payment::getCreatedAt, Timestamp.from(before));
    return paymentRepository.findByCriteria(criteria).stream()
        .map(PaymentQueryAdapter::toProjection)
        .toList();
  }

  private static PaymentProjection toProjection(Payment p) {
    return new PaymentProjection(
        p.getTenantId(),
        p.getId(),
        p.getOrderId(),
        p.getCustomerId(),
        p.getAmount(),
        p.getChannel() == null ? null : p.getChannel().name(),
        p.getStatus() == null ? null : p.getStatus().name(),
        p.getChannelTradeNo(),
        p.getPayUrl(),
        p.getPaidAt(),
        p.getRefundedAt(),
        p.getRefundAmount(),
        p.getCreatedAt());
  }
}
