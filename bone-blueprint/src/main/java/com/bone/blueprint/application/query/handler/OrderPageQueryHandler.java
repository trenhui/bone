package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.application.query.support.OrderSummaryAssembler;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单分页查询（读侧）。
 *
 * <p><b>读侧实现选择标准</b>（样板约定，§18.5）：简单单表条件查询 → {@link QueryBuilder} DSL 直查聚合（本类示范）； 多表 Join / 复杂投影 →
 * {@code domain/gateway/*ReadPort} + 原生 SQL（见 {@code OrderDetailQueryHandler}）。
 *
 * <p>组装统一收敛在 {@code application/query/support} 静态 assembler（{@link OrderSummaryAssembler}），Handler
 * 内不写私有组装方法。
 */
@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {

  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<OrderDto> handle(OrderPageQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    FluentQuery<Order> fluentQuery =
        QueryBuilder.from(Order.class).where(Order::getTenantId).eq(tenantId);

    if (query.customerId() != null) {
      fluentQuery.where(Order::getCustomerId).eq(query.customerId());
    }
    if (query.status() != null && !query.status().isBlank()) {
      OrderStatus status = OrderStatus.valueOf(query.status());
      fluentQuery.where(Order::getStatus).eq(status);
    }

    int pageNum = query.pageNum() != null ? query.pageNum() : 1;
    int pageSize = query.pageSize() != null ? query.pageSize() : 10;

    com.bone.core.model.PageResult<Order> page =
        fluentQuery.orderByDesc(Order::getCreatedAt).page(pageNum, pageSize);

    List<OrderDto> records =
        page.getRecords().stream().map(OrderSummaryAssembler::fromOrder).toList();
    return PageResult.of(records, page.getTotal(), pageNum, pageSize);
  }
}
