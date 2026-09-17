package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.dto.OrderWithItemsProjection;
import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.query.support.OrderDetailAssembler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderDetailQueryHandler {

  private final OrderQueryPort orderQueryPort;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public OrderDto handle(OrderDetailQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    List<OrderWithItemsProjection> rows =
        orderQueryPort.findOrderWithItems(tenantId, query.orderId());
    return OrderDetailAssembler.fromRows(rows);
  }
}
