package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.query.support.OrderDetailAssembler;
import com.bone.blueprint.domain.gateway.OrderReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderDetailQueryHandler {

  private final OrderReadPort orderReadPort;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public OrderDto handle(OrderDetailQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    List<OrderWithItemsRow> rows = orderReadPort.findOrderWithItems(tenantId, query.getOrderId());
    return OrderDetailAssembler.fromRows(rows);
  }
}
