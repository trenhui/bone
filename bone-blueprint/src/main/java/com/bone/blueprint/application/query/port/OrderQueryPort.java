package com.bone.blueprint.application.query.port;

import com.bone.blueprint.application.query.projection.OrderHeadProjection;
import com.bone.blueprint.application.query.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 订单读侧端口（E-13.3 *QueryPort）。
 *
 * <p>读模型直查，不经过写聚合；同时承载超时订单扫描（供定时任务使用），避免写仓储堆砌多条件查询方法 （违反仓储方法白名单）。
 */
public interface OrderQueryPort {

  /** 订单头 + 明细联表投影（LEFT JOIN，无明细时 itemId 为 null）。 */
  List<OrderWithItemsProjection> findOrderWithItems(long tenantId, long orderId);

  /**
   * 查询指定时间之前仍处于 CREATED 的订单（<b>全租户</b>，供运维型定时任务扫描）。
   *
   * <p><b>为何需要全租户方法</b>：定时线程没有请求上下文，{@code TenantPort} 会降级为平台租户（0），
   * 若仍按"当前租户"扫描，除平台租户外的超时订单将<strong>永不取消</strong>，且日志看起来一切正常。 全租户扫描属"平台运维入口"，调用方须为定时任务并在 README
   * 打洞登记中说明。
   */
  List<OrderHeadProjection> findCreatedExpiredBeforeAllTenants(Instant before);

  /**
   * 查询指定订单的当前状态（供对账：判断"支付已成功但订单是否已确认支付"）。
   *
   * @return 订单不存在（或不可见）时返回 {@code Optional.empty()}
   */
  Optional<OrderStatus> findStatusById(long tenantId, long orderId);

  /** 分页查询订单头投影。 */
  PageResult<OrderHeadProjection> findOrderPage(
      long tenantId, Long customerId, OrderStatus status, int pageNum, int pageSize);
}
