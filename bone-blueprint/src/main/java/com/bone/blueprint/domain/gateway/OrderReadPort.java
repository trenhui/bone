package com.bone.blueprint.domain.gateway;

import com.bone.blueprint.domain.order.read.OrderHeadRow;
import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import java.time.Instant;
import java.util.List;

/**
 * 订单读侧端口（§18.5 *ReadPort）。
 *
 * <p>读模型直查，不经过写聚合；同时承载超时订单扫描（供超时取消定时任务使用），避免写仓储堆砌多条件 查询方法（违反仓储方法白名单）。
 */
public interface OrderReadPort {

  /** 订单 + 明细 Join 投影。 */
  List<OrderWithItemsRow> findOrderWithItems(long tenantId, long orderId);

  /**
   * 订单头分页投影（E-9.3 目标态：简单单表查询也走 ReadPort，读侧 DSL 只许出现在 {@code infrastructure/query}）。
   *
   * @param tenantId 租户
   * @param customerId 客户过滤（可空）
   * @param status 状态过滤（可空）
   * @param pageNum 页码（从 1 起）
   * @param pageSize 每页条数
   */
  PageResult<OrderHeadRow> findOrderPage(
      long tenantId, Long customerId, OrderStatus status, int pageNum, int pageSize);

  /**
   * 查询指定时间之前仍处于待支付（CREATED）状态的订单（供超时取消扫描）。
   *
   * @param tenantId 租户
   * @param before 时间边界（createdAt 早于该时间视为超时）
   */
  List<OrderHeadRow> findCreatedExpiredBefore(long tenantId, Instant before);
}
