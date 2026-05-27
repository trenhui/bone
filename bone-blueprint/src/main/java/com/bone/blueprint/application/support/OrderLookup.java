package com.bone.blueprint.application.support;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import java.util.Objects;
import java.util.Optional;

/**
 * 订单加载辅助（应用层统一 Optional 语义 + 租户隔离）。
 */
public final class OrderLookup {

    private OrderLookup() {}

    public static Order requireById(OrderRepository repository, Long orderId) {
        Order order = Optional.ofNullable(repository.findById(orderId))
                .orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
        Long tenantId = order.getTenantId();
        long currentTenantId = TenantSupport.currentTenantId();
        if (tenantId != null && !Objects.equals(tenantId, currentTenantId)) {
            throw new NotFoundException("订单不存在: " + orderId);
        }
        return order;
    }
}
