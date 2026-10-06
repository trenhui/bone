package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.projection.OrderHeadProjection;
import com.bone.blueprint.domain.model.order.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.util.List;

/**
 * 订单仓储（ADR-0030：写侧与本聚合读模型合并为单一仓储）。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>写走 SDK 原生 {@code update(entity)}（{@code @Version} 乐观锁），读返回领域投影。SQL 模板固定为 {@code
 * resources/sql/…/OrderRepository/<方法名>.sql}，禁止 {@code @Sql} 注解（影子 SQL）。全租户扫描方法显式标注
 * {@code @TenantScope(ALL)}，由 {@code ArchitectureTest} 门禁识别并限制调用方为 {@code adapter.schedule}。
 */
public interface OrderRepository extends Repository<Order, Long> {

  /**
   * 订单头 + 明细扁平投影（每明细一行，头字段逐行重复）。
   *
   * <p>AUTO 租户：SQL 模板放 {@code /*bone:tenant*}{@code /} 锚点，SDK 自动注入 {@code o.tenant_id =
   * :__boneTenantId__}。调用方不再需要传 tenantId。
   */
  @TenantScope(value = TenantScopeMode.AUTO, column = "o.tenant_id")
  List<OrderWithItemsProjection> findOrderWithItems(@Param("orderId") long orderId);

  /**
   * 超时未支付订单扫描（全租户，定时任务专用）：{@code status = CREATED} 且 {@code created_at < before}。 SQL 见 {@code
   * resources/sql/…/OrderRepository/findExpiredOrdersAllTenants.sql}。
   */
  @TenantScope(TenantScopeMode.ALL)
  List<OrderHeadProjection> findExpiredOrdersAllTenants(@Param("before") Timestamp before);

  /**
   * 按「渠道 + 渠道原始订单号」查订单（渠道拉单幂等键）。
   *
   * <p>渠道推送存在<strong>必然重复</strong>（网络重试、渠道侧重推），若不做幂等会产生重复订单。 数据库层已建 {@code
   * uk_order_tenant_channel_order_no} 唯一约束兜底， 此处的前置查询是为了<strong>返回已有订单号而不是抛唯一键冲突</strong>——
   * 让「重复推送」成为正常的幂等响应，而不是 5xx。
   */
  default Order findByChannelOrderNo(Long tenantId, String channelCode, String channelOrderNo) {
    Criteria<Order> criteria =
        Criteria.<Order>create()
            .eq(Order::getTenantId, tenantId)
            .eq(Order::getChannelCode, channelCode)
            .eq(Order::getChannelOrderNo, channelOrderNo);
    return findOneByCriteria(criteria);
  }

  /** 订单头分页；{@code customerId} / {@code status} 为可选过滤条件。 */
  default PageResult<OrderHeadProjection> findOrderPage(
      long tenantId, Long customerId, OrderStatus status, int page, int size) {
    Criteria<Order> criteria =
        Criteria.<Order>create()
            .eq(Order::getTenantId, tenantId)
            .eq(customerId != null, Order::getCustomerId, customerId)
            .eq(status != null, Order::getStatus, status)
            .orderByDesc(Order::getCreatedAt)
            .page(page, size);
    return pageByCriteria(criteria).map(OrderHeadProjection::from);
  }
}
