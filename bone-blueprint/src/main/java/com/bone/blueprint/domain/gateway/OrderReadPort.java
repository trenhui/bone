package com.bone.blueprint.domain.gateway;

import com.bone.blueprint.domain.order.read.OrderHeadRow;
import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
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
   * 查询指定时间之前仍处于待支付（CREATED）状态的订单（供超时取消扫描）。
   *
   * @param tenantId 租户
   * @param before 时间边界（createdAt 早于该时间视为超时）
   */
  List<OrderHeadRow> findCreatedExpiredBefore(long tenantId, Instant before);
}
