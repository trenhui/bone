package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.order.Order;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 订单写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>多租户隔离下沉到仓储查询层：{@link #findByIdInTenant} 用 SDK Criteria 附加租户条件， 跨租户订单在 SQL 层面即被过滤（返回
 * null），而非加载后手动校验。
 */
public interface OrderRepository extends Repository<Order, Long> {

  /**
   * 按 id 加载当前租户可访问的订单。
   *
   * <p>仅返回 {@code id} 与 {@code tenantId} 同时匹配的订单；跨租户或不存在时返回 {@code null} （与 {@link #findById}
   * 的空语义一致）。
   */
  default Order findByIdInTenant(Long id, Long tenantId) {
    // 用字符串字段名（避免 lambda 方法引用生成 $deserializeLambda$ 合成方法，
    // 否则违反仓储方法白名单 ArchUnit 规则；字段名经 @Column/snake_case 映射到真实列）
    return findOneByCriteria(
        Criteria.<Order>create().entityClass(Order.class).eq("id", id).eq("tenantId", tenantId));
  }
}
