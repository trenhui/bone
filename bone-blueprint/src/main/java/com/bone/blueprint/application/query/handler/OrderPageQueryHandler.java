package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.application.query.support.OrderSummaryAssembler;
import com.bone.blueprint.domain.gateway.OrderReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.read.OrderHeadRow;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.exception.InvalidRequestException;
import com.bone.core.model.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单分页查询（读侧）。
 *
 * <p><b>读侧实现选择标准</b>（样板约定，§18.5）：无论单表还是多表投影，一律走 {@code domain/gateway/*ReadPort} + 原生 SQL（见 {@code
 * infrastructure/query/OrderReadPortImpl}），读侧 DSL 只许出现在 {@code infrastructure/query}（E-9.3，ArchUnit
 * #19 门禁）。
 *
 * <p>组装统一收敛在 {@code application/query/support} 静态 assembler（{@link OrderSummaryAssembler}），Handler
 * 内不写私有组装方法。
 */
@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {

  private final OrderReadPort orderReadPort;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<OrderDto> handle(OrderPageQuery query) {
    long tenantId = tenantProvider.currentTenantId();

    Long customerId = query.customerId();
    OrderStatus status = parseStatus(query.status());

    int pageNum = query.pageNum() != null ? query.pageNum() : 1;
    int pageSize = query.pageSize() != null ? query.pageSize() : 10;

    PageResult<OrderHeadRow> page =
        orderReadPort.findOrderPage(tenantId, customerId, status, pageNum, pageSize);

    List<OrderDto> records =
        page.getRecords().stream().map(OrderSummaryAssembler::fromRow).toList();
    return PageResult.of(records, page.getTotal(), pageNum, pageSize);
  }

  /**
   * 解析状态入参：非法值转 {@link InvalidRequestException}（映射 400）。
   *
   * <p>直接 {@code OrderStatus.valueOf} 会抛 {@code IllegalArgumentException}，被全局处理器映射成 5xx——
   * 把「调用方传错参数」报成「服务端故障」，既误导排查也会污染告警。
   */
  private static OrderStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return OrderStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new InvalidRequestException("订单状态非法: " + status);
    }
  }
}
