package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.payment.Payment;
import com.bone.core.enums.Operator;
import com.bone.core.model.PageResult;
import com.bone.core.model.QueryParam;
import com.bone.metadata.sdk.Repository;
import java.util.List;

/**
 * 支付单写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>多租户隔离下沉到仓储查询层：{@link #findByIdInTenant} 用通用条件查询在 SQL 层附加租户条件（与 {@code
 * OrderRepository.findByIdInTenant} 同模式）。
 *
 * <p><b>为何不用 {@code Criteria} / {@code QueryBuilder}</b>：{@code com.bone.metadata.sdk.query.*} 属读侧
 * DSL（{@code @ReadSideOnly}），domain 层禁止依赖（CORE-05）。改用 bone-core 的 {@link QueryParam} + {@link
 * Operator} 表达条件。
 */
public interface PaymentRepository extends Repository<Payment, Long> {

  /**
   * 按 id 加载当前租户可访问的支付单。
   *
   * <p>仅返回 {@code id} 与 {@code tenantId} 同时匹配、且未被软删的支付单；跨租户或不存在时返回 {@code null}。
   *
   * <p><b>前置判空不是冗余防御，而是失败关闭</b>：{@code queryByCondition} 会静默丢弃 {@code null} 值条件 （见 {@code
   * BaseRepository#buildCriteria}），租户缺失时租户条件会整条消失、查询退化为跨租户按 id 读取； 旧 {@code Criteria.eq("tenantId",
   * null)} 生成恒假条件天然失败关闭。两者不等价，故显式补回。
   */
  default Payment findByIdInTenant(Long id, Long tenantId) {
    if (id == null || tenantId == null) {
      return null;
    }
    PageResult<Payment> page =
        queryByCondition(
            List.of(
                new QueryParam("id", id, Operator.EQ),
                new QueryParam("tenantId", tenantId, Operator.EQ)),
            null,
            1,
            1,
            null);
    return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
  }
}
