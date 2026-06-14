package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.application.support.TenantSupport;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<OrderDto> handle(OrderPageQuery query) {
        long tenantId = TenantSupport.currentTenantId();
        FluentQuery<Order> fluentQuery =
                QueryBuilder.from(Order.class).where(Order::getTenantId).eq(tenantId);

        if (query.getCustomerId() != null) {
            fluentQuery.where(Order::getCustomerId).eq(query.getCustomerId());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            OrderStatus status = OrderStatus.valueOf(query.getStatus());
            fluentQuery.where(Order::getStatus).eq(status);
        }

        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        com.bone.core.model.PageResult<Order> page =
                fluentQuery.orderByDesc(Order::getCreatedAt).page(pageNum, pageSize);

        List<OrderDto> records = page.getRecords().stream().map(this::toSummaryDto).toList();
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    private OrderDto toSummaryDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .build();
    }
}
