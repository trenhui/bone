package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.order.Order;
import com.bone.core.enums.Operator;
import com.bone.core.model.PageResult;
import com.bone.core.model.QueryParam;
import com.bone.metadata.sdk.Repository;
import java.util.List;

/**
 * 订单写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>多租户隔离下沉到仓储查询层：{@link #findByIdInTenant} 用通用条件查询在 SQL 层附加租户条件，跨租户订单直接被 过滤（返回 {@code
 * null}），而非加载后手动校验。
 *
 * <p><b>为何不用 {@code Criteria} / {@code QueryBuilder}</b>：{@code com.bone.metadata.sdk.query.*} 属读侧
 * DSL（{@code @ReadSideOnly}），domain 层禁止依赖（CORE-05）。改用 bone-core 的 {@link QueryParam} + {@link
 * Operator} 表达条件——仍走同一条条件管线、同样的软删过滤，且不把读侧 DSL 引入 domain。
 */
public interface OrderRepository extends Repository<Order, Long> {

  /**
   * 按 id 加载当前租户可访问的订单。
   *
   * <p>仅返回 {@code id} 与 {@code tenantId} 同时匹配、且未被软删的订单；跨租户或不存在时返回 {@code null} （与 {@link #findById}
   * 的空语义一致）。
   *
   * <p><b>前置判空不是冗余防御，而是失败关闭</b>：{@code queryByCondition} 会<strong>静默丢弃</strong>值为 {@code null}
   * 的条件（见 {@code BaseRepository#buildCriteria} 的 {@code value != null} 判断）。若租户缺失， WHERE
   * 中的租户条件会整条消失，查询退化为「按 id 跨租户读取」——多租户隔离在此<strong>失败开启</strong>。 而旧写法 {@code
   * Criteria.eq("tenantId", null)} 生成 {@code tenant_id = NULL} 恒假条件，天然失败关闭。
   * 两者语义<strong>并不等价</strong>，故显式补回：租户不可知即视为不可访问。
   */
  default Order findByIdInTenant(Long id, Long tenantId) {
    if (id == null || tenantId == null) {
      return null;
    }
    PageResult<Order> page =
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
