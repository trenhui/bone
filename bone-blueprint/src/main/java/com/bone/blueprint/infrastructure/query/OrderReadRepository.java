package com.bone.blueprint.infrastructure.query;

import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.application.query.projection.OrderHeadProjection;
import com.bone.blueprint.application.query.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 订单读侧仓储：同时是 {@link OrderQueryPort} 的基础设施实现（E-13.3 + E-10.3，{@code infrastructure/query}）。
 *
 * <p><b>为什么把端口实现也收进这一个接口</b>：原来 {@code OrderQueryPort} 由 {@code OrderQueryAdapter} 实现， adapter
 * 只是把端口方法逐个转发到 {@code OrderReadRepository}（@Sql 投影）与写侧 {@code OrderRepository}（Criteria 简单读），
 * 是纯粹的转发样板。本接口本身继承 {@code Repository<Order,Long>}，已原生拥有 Criteria 通道 （{@code
 * findOneByCriteria}/{@code pageByCriteria}），因此<strong>简单读用 {@code default} 方法直接走自身继承的
 * Criteria</strong>、JOIN/全租户投影用 {@code @Sql}，无需再引入一个只做转发的 adapter 类。
 *
 * <p><b>本通道不注入租户、不注入软删</b>：SQL 所见即所得。租户内查询必须自己写 {@code AND o.tenant_id = #{tenantId}}；全租户方法不写
 * {@code tenant_id} 即为全租户—— 这是定时任务扫描需要的语义，属平台运维入口，调用方须登记。 简单读（{@code findStatusById}/{@code
 * findOrderPage}）走继承的 Criteria 通道，租户由 ADR-0029 自动注入。
 *
 * <p><b>列别名必须匹配投影字段名</b>：结果由 {@code SmartRowMapper} 按列标签映射（下划线转驼峰）， 所以 {@code o.id AS order_id}
 * 这类别名不能省，否则 {@code orderId} 会一直是 null。
 *
 * <p><b>返回的是「每明细一行」的扁平行集，不是单个订单</b>：N 条明细 → N 行、头字段逐行重复；无明细时 返回 1 行且 {@code itemId} 为 {@code
 * null}。折叠成 {@code OrderDto} 的责任<strong>只在</strong> {@code OrderDetailAssembler#fromRows}（它用 {@code
 * rows.get(0)} 取头、再按 {@code itemId != null} 收集明细），其它调用方不得把这组行当「一行一单」或单值使用。
 *
 * <p><b>模板源二选一，禁止并存</b>：模板 ID 固定为 {@code {接口全限定名}.{方法名}}（{@code RepositoryFactoryBean} 在生成代理时
 * 拼装并<strong>立即加载</strong>）。当前全部 SQL 走方法上的 {@code @Sql} 内联——都是短静态 SQL，且与租户/软删约定同处一屏便于 Review。若要改为外置
 * 文件（默认目录 {@code
 * resources/sql/com/bone/blueprint/infrastructure/query/OrderReadRepository/<方法名>.sql}），必须先删掉对应的
 * {@code @Sql} 注解：默认 {@code load-priority=annotation-first}，注解命中后文件<strong>永不加载且不报错</strong>，
 * 会变成一份悄悄漂移的影子 SQL。另注意文件模式下<strong>重命名方法会导致启动失败</strong>（路径失配 → {@code TemplateNotFoundException}），
 * 而内联模式不存在这层隐式字符串耦合。
 *
 * <p><b>本接口由 {@code @EnableSqlRepositories} 扫描并生成 JDK 代理</b>（须在启动类的 {@code basePackages} 中登记本包）；
 * {@code default} 方法由代理绑定接收者后调用（见 SDK {@code DefaultMethodHandler}），可安全调用自身继承的 Criteria 方法。
 */
public interface OrderReadRepository extends Repository<Order, Long>, OrderQueryPort {

  /**
   * 订单头 + 明细联表投影（LEFT JOIN，无明细时 {@code itemId} 为 {@code null}）。
   *
   * <p>带 {@code tenant_id}：租户内的详情查询，不是全租户。
   */
  @Sql(
      """
      SELECT
          o.id AS order_id,
          o.customer_id,
          o.total_amount,
          o.status,
          o.created_at,
          oi.id AS item_id,
          oi.product_id,
          oi.product_name,
          oi.quantity,
          oi.unit_price,
          oi.subtotal
      FROM t_order o
      LEFT JOIN t_order_item oi ON o.id = oi.order_id AND oi.deleted = 0
      WHERE o.id = #{orderId}
        AND o.tenant_id = #{tenantId}
        AND o.deleted = 0
      ORDER BY oi.id
      """)
  @Override
  List<OrderWithItemsProjection> findOrderWithItems(
      @Param("tenantId") long tenantId, @Param("orderId") long orderId);

  /**
   * 超时未支付订单扫描（<b>全租户</b>）：不带 {@code tenant_id} 条件，供已登记的运维型定时任务使用。
   *
   * <p>为何必须全租户：定时线程没有请求上下文，按"当前租户"扫描只会落到降级后的平台租户 0， 其余租户的超时订单将永不取消，且日志看起来一切正常。
   *
   * <p>端口入参为 {@code Instant}，由本 {@code default} 方法转 {@code Timestamp} 后交给 {@code @Sql} 方法——与改造前一致，
   * 显式绑定 DATETIME，不依赖驱动对 {@code java.time.Instant} 的推断。
   */
  @Override
  default List<OrderHeadProjection> findCreatedExpiredBeforeAllTenants(Instant before) {
    return findCreatedExpiredBeforeAllTenantsSql(Timestamp.from(before));
  }

  /** 全租户超时不支付扫描的 {@code @Sql} 实现（详见 {@link #findCreatedExpiredBeforeAllTenants}）。 */
  @Sql(
      """
      SELECT
          tenant_id,
          id AS order_id,
          customer_id,
          total_amount,
          status,
          created_at
      FROM t_order
      WHERE deleted = 0
        AND status = 'CREATED'
        AND created_at < #{before}
      """)
  List<OrderHeadProjection> findCreatedExpiredBeforeAllTenantsSql(
      @Param("before") Timestamp before);

  /**
   * 查询指定订单的当前状态（供对账：判断"支付已成功但订单是否已确认支付"）。
   *
   * <p>单表 → 走自身继承的 SDK {@link Criteria} 通道：租户由 ADR-0029 自动注入（无上下文且 caller
   * 未限定租户时<strong>失败关闭</strong>）、 软删自动追加。租户与 id 一并下发，缺租户时不会退化成跨租户读取。
   *
   * @return 订单不存在（或不可见）时返回 {@code Optional.empty()}
   */
  @Override
  default Optional<OrderStatus> findStatusById(long tenantId, long orderId) {
    Order order =
        this.findOneByCriteria(
            Criteria.<Order>create().eq(Order::getId, orderId).eq(Order::getTenantId, tenantId));
    return Optional.ofNullable(order).map(Order::getStatus);
  }

  /**
   * 分页查询订单头投影。
   *
   * <p>动态条件用 Criteria 的条件开关（首个 boolean 参数）表达；{@code pageByCriteria} 自带 count。 投影映射只在读侧（本接口），读侧不向
   * adapter 泄露聚合。
   */
  @Override
  default PageResult<OrderHeadProjection> findOrderPage(
      long tenantId, Long customerId, OrderStatus status, int pageNum, int pageSize) {
    Criteria<Order> criteria =
        Criteria.<Order>create()
            .eq(Order::getTenantId, tenantId)
            .eq(customerId != null, Order::getCustomerId, customerId)
            .eq(status != null, Order::getStatus, status)
            .orderByDesc(Order::getCreatedAt)
            .page(pageNum, pageSize);
    // 聚合只在读侧被映射成投影，不向外透出可变聚合；映射内联于此（不放接口 static 方法——
    // SDK 代理工厂会遍历接口方法并尝试为无 @Sql 的方法加载模板，static 方法会导致 Bean 初始化失败）。
    return this.pageByCriteria(criteria)
        .map(
            o ->
                new OrderHeadProjection(
                    o.getTenantId(),
                    o.getId(),
                    o.getCustomerId(),
                    o.getTotalAmount(),
                    o.getStatus() == null ? null : o.getStatus().name(),
                    o.getCreatedAt() == null
                        ? null
                        : LocalDateTime.ofInstant(o.getCreatedAt(), ZoneId.systemDefault())));
  }
}
