package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.projection.OrderHeadProjection;
import com.bone.blueprint.domain.order.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储（ADR-0030：写侧与本聚合读模型合并为单一仓储）。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>类内只写契约与陷阱，实现取舍见 ADR-0030：
 *
 * <ul>
 *   <li>写：{@link #findByIdInTenant} 租户内加载 + SDK 原生 {@code @Version} 乐观锁（{@code update(entity)} 由
 *       {@code bone-metadata-sdk} 维护版本，应用层翻译冲突为 {@code OptimisticLockConflictException}）。
 *   <li>读：返回领域读模型，不向外透出可变聚合；单表读走继承的 Criteria，联表 / 全租户读走外置 {@code .sql}。
 *   <li>SQL 真源唯一：模板固定为 {@code resources/sql/…/OrderRepository/<方法名>.sql}，<b>禁止再写 {@code @Sql}</b>——
 *       默认 {@code classpath-first}，两源并存时注解那份永不加载且不报错（影子 SQL），见 ADR-0030 §1.4 / R3。
 *   <li>租户：{@code @TenantScope} 必须显式标注（缺省 {@link TenantScopeMode#MANUAL} 不报错，但会失去声明）； 只有 {@code
 *       AUTO} 由 SDK 注入租户，{@code MANUAL} / {@code ALL} 的租户条件与软删全靠 SQL 自己写。 Criteria 方法则显式下发
 *       tenantId。
 *   <li>查不到返回 {@code Optional.empty()}，不返回 {@code null}——与 SDK {@code findById} 的 null 语义区分开。
 *   <li>接口内禁用 {@code static} 与 {@code private} 方法——SDK 代理会为它们加载 SQL 模板导致 Bean 初始化失败；映射逻辑放读模型的静态工厂。
 * </ul>
 */
public interface OrderRepository extends Repository<Order, Long> {

  /**
   * 按 id 加载租户内订单；id / tenantId 为空、跨租户、已软删时返回 {@code Optional.empty()}。
   *
   * <p><b>不能用 SDK {@code findById} 替代</b>：{@code findById} 的租户取自 {@code TenantContext} 线程变量， 事件订阅 /
   * 定时任务 / Outbox 中继等异步入口没有上下文，会直接 {@code MissingTenantContextException}；
   * 且它无法表达「指定租户」。本方法把租户作为显式参数（E-2）。
   *
   * <p><b>失败关闭</b>：{@code Criteria.eq} 会静默丢弃 {@code null} 条件，租户缺失时条件整条消失即退化为跨租户读取，故必须显式判空。
   */
  default Optional<Order> findByIdInTenant(Long id, Long tenantId) {
    if (id == null || tenantId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<Order>create().eq(Order::getId, id).eq(Order::getTenantId, tenantId)));
  }

  /**
   * 订单头 + 明细扁平投影（每明细一行，头字段逐行重复；无明细时明细列为 {@code null}）。
   *
   * <p>MANUAL 租户：SQL 自带 {@code tenant_id} 与子表 {@code deleted = 0}，租户由调用方显式传入。
   *
   * <p>SQL 见 {@code
   * resources/sql/com/bone/blueprint/domain/repository/OrderRepository/findOrderWithItems.sql}。
   */
  @TenantScope(TenantScopeMode.MANUAL)
  List<OrderWithItemsProjection> findOrderWithItems(
      @Param("tenantId") long tenantId, @Param("orderId") long orderId);

  /** 超时订单扫描（全租户，定时任务专用，授权登记见 E-2）；{@code Instant → Timestamp} 适配在此完成。 */
  default List<OrderHeadProjection> findCreatedExpiredBeforeAllTenants(Instant before) {
    return findCreatedExpiredBeforeAllTenantsSql(Timestamp.from(before));
  }

  /**
   * 全租户扫描的模板 SQL 实现。
   *
   * <p>ALL：定时线程无请求上下文，按"当前租户"扫描只会落到平台租户 0、其余租户超时订单永不取消，故必须显式全租户。
   *
   * <p>SQL 见 {@code resources/sql/…/OrderRepository/findCreatedExpiredBeforeAllTenantsSql.sql}。
   */
  @TenantScope(TenantScopeMode.ALL)
  List<OrderHeadProjection> findCreatedExpiredBeforeAllTenantsSql(
      @Param("before") Timestamp before);

  /** 订单当前状态（对账用）；订单不存在或不可见时返回 {@code Optional.empty()}。 */
  default Optional<OrderStatus> findStatusById(long tenantId, long orderId) {
    Order order =
        findOneByCriteria(
            Criteria.<Order>create().eq(Order::getId, orderId).eq(Order::getTenantId, tenantId));
    return Optional.ofNullable(order).map(Order::getStatus);
  }

  /** 订单头分页；条件用 Criteria 开关表达，租户显式下发，投影映射由 {@link OrderHeadProjection#from} 承担。 */
  default PageResult<OrderHeadProjection> findOrderPage(
      long tenantId, Long customerId, OrderStatus status, int pageNum, int pageSize) {
    Criteria<Order> criteria =
        Criteria.<Order>create()
            .eq(Order::getTenantId, tenantId)
            .eq(customerId != null, Order::getCustomerId, customerId)
            .eq(status != null, Order::getStatus, status)
            .orderByDesc(Order::getCreatedAt)
            .page(pageNum, pageSize);
    return pageByCriteria(criteria).map(OrderHeadProjection::from);
  }
}
