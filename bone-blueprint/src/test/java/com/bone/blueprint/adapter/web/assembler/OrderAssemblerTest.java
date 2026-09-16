package com.bone.blueprint.adapter.web.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bone.blueprint.adapter.web.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.web.dto.request.OrderPageQry;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * adapter 装配器测试（MapStruct 实现 + default 方法）。
 *
 * <p>锁住两件容易在重构中被悄悄改掉的事：① {@code *Req} → {@code *Command} 的字段映射完整（漏字段会变成线上"参数没生效"）； ② HTTP
 * 入口的命令<strong>不携带租户</strong>（租户由请求上下文提供），而定时任务入口才显式传——两者混用会造成越权读写。
 */
class OrderAssemblerTest {

  private final OrderAssembler assembler = new OrderAssemblerImpl();

  @Test
  void mapsCreateRequestToCommandIncludingItems() {
    CreateOrderReq request = new CreateOrderReq();
    request.setCustomerId(5L);
    CreateOrderReq.OrderItemReq item = new CreateOrderReq.OrderItemReq();
    item.setProductId(7L);
    item.setProductName("样例商品");
    item.setQuantity(2);
    item.setUnitPrice(new BigDecimal("10.00"));
    request.setItems(List.of(item));

    CreateOrderCommand command = assembler.toCreateOrderCommand(request);

    assertEquals(5L, command.customerId());
    assertEquals(1, command.items().size());
    assertEquals(7L, command.items().get(0).productId());
    assertEquals("样例商品", command.items().get(0).productName());
    assertEquals(2, command.items().get(0).quantity());
    assertEquals(new BigDecimal("10.00"), command.items().get(0).unitPrice());
  }

  @Test
  void mapsPageQueryKeepingFiltersAndPaging() {
    OrderPageQry qry = new OrderPageQry();
    qry.setCustomerId(5L);
    qry.setStatus("PAID");
    qry.setPageNum(2);
    qry.setPageSize(50);

    OrderPageQuery query = assembler.toOrderPageQuery(qry);

    assertEquals(5L, query.customerId());
    assertEquals("PAID", query.status());
    assertEquals(2, query.pageNum());
    assertEquals(50, query.pageSize());
  }

  @Test
  void httpEntryCommandsDoNotCarryTenant() {
    CancelOrderCommand command = assembler.toCancelOrderCommand(9L);

    assertEquals(9L, command.orderId());
    // HTTP 入口不带 tenantId：由 Handler 从请求上下文取（定时任务入口才显式传）
    assertNull(command.tenantId());
  }
}
