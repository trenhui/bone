package com.bone.blueprint.application.support;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;

import java.util.Optional;

/**
 * 订单加载辅助（应用层统一 Optional 语义，禁止 null 表达「未找到」）。
 */
public final class OrderLookup {

    private OrderLookup() {}

    public static Order requireById(OrderRepository repository, Long orderId) {
        return Optional.ofNullable(repository.findById(orderId))
                .orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
    }
}
