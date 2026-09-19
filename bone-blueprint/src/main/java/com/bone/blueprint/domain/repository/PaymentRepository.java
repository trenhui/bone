package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.projection.PaymentProjection;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 支付单仓储（ADR-0030：写侧与本聚合读模型合并为单一仓储）。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories}
 * 代理实现；约定同 {@link OrderRepository}（加载走租户内条件查询、更新走 SDK 原生 {@code @Version} 乐观锁（{@code
 * update(entity)}），接口内不写 {@code static} / {@code private} 方法）。
 *
 * <p><b>为何两个全租户扫描方法在这里，而不是独立读侧端口</b>：它们是同一个 {@code bp_payment} 的单表读，既不 JOIN、也不跨聚合——按 ADR-0030「本聚合读
 * → 域仓储；跨聚合 / 报表 / 搜索 → QueryPort」的判据，它们没有读模型分歧，不构成 QueryPort 存在的理由。此前它们挂在 {@code PaymentQueryPort}
 * + {@code infrastructure/query/PaymentQueryAdapter} 上是合并前的存量形态（ADR-0030 §P4 记为待办），现已按同一形态折叠。
 *
 * <p><b>通道选择：Criteria 而非外置 {@code .sql}</b>——这两条扫描只过滤本表单列，Criteria 已能表达，且租户逃生舱（{@code
 * disableTenantFilter()}）与软删（{@code deleted = 0}）由 SDK 统一处理；写外置 SQL 反而要手写这两件事，多一处漏写的可能。对照 {@link
 * OrderRepository#findCreatedExpiredBeforeAllTenants}：那里同样全租户，但只取 4 列投影，走
 * {@code @Sql}。两条通道并存不是不一致，是按 「Criteria 表达得了就不写 SQL」选的（ADR-0030 §0.3）。
 *
 * <p><b>方法名后缀 {@code AllTenants} 是门禁判据，不可改</b>：{@code all_tenants_scan_only_by_schedule} 按「方法名以
 * {@code AllTenants} 结尾」识别全租户入口，并把调用方限制在 {@code adapter.schedule}。后缀去掉（例如改成 {@code
 * findExpiredPayments}）不会报错，只会让这道 E-2 防护<strong>静默失效</strong>。
 *
 * <p><b>只能由定时任务调用</b>：定时线程无请求上下文，这两个方法显式关闭租户过滤。若被 web / rpc / messaging 入口调到，等于在请求链路上 泄露跨租户数据。
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

  /**
   * 指定时间之前仍处于 PENDING / PAYING 的支付单（<b>全租户</b>，供超时关闭定时任务扫描）。
   *
   * <p>{@code disableTenantFilter()} 是 ADR-0029 的显式逃生舱（会打 WARN，可在审计侧检索）；软删仍由 SDK 自动追加。它与 SQL 通道的
   * {@code @TenantScope(ALL)} 角色相同，差别只在通道。
   */
  default List<PaymentProjection> findPayableExpiredBeforeAllTenants(Instant before) {
    Criteria<Payment> criteria =
        Criteria.<Payment>create()
            .disableTenantFilter()
            .in(Payment::getStatus, PaymentStatus.PENDING, PaymentStatus.PAYING)
            .lt(Payment::getCreatedAt, Timestamp.from(before));
    return findByCriteria(criteria).stream().map(PaymentProjection::from).toList();
  }

  /**
   * 指定时间之前已 SUCCESS 的支付单（<b>全租户</b>，供「钱货不一致」对账扫描）。
   *
   * <p>对账用途：支付单已成功但订单仍停在 CREATED，说明支付成功事件的下游链路（订单确认）未执行成功，必须留痕告警——只打 info 日志会让这类资金异常永久沉没。
   */
  default List<PaymentProjection> findSuccessCreatedBeforeAllTenants(Instant before) {
    Criteria<Payment> criteria =
        Criteria.<Payment>create()
            .disableTenantFilter()
            .eq(Payment::getStatus, PaymentStatus.SUCCESS)
            .lt(Payment::getCreatedAt, Timestamp.from(before));
    return findByCriteria(criteria).stream().map(PaymentProjection::from).toList();
  }
}
