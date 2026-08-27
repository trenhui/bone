package com.bone.blueprint.domain.service;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import java.util.Objects;
import java.util.Optional;

/**
 * 订单加载领域服务（领域层承载租户隔离这一业务规则）。
 *
 * <p>聚合按 ID 加载后做租户归属校验：跨租户访问视为不存在。租户 ID 由应用层（Handler）从 {@code infrastructure.context.TenantSupport}
 * 获取后传入，领域层不依赖基础设施。
 */
public final class OrderLookup {

  private OrderLookup() {}

  public static Order requireById(OrderRepository repository, Long orderId, Long tenantId) {
    Order order =
        Optional.ofNullable(repository.findById(orderId))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
    if (order.getTenantId() != null && !Objects.equals(order.getTenantId(), tenantId)) {
      throw new NotFoundException("订单不存在: " + orderId);
    }
    return order;
  }
}
