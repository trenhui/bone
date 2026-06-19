package com.bone.blueprint.domain.gateway;

import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
import java.util.List;

/** 订单读侧端口（Join 投影，§18.5 *ReadPort）。 */
public interface OrderReadPort {

  List<OrderWithItemsRow> findOrderWithItems(long tenantId, long orderId);
}
