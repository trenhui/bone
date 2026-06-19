package com.bone.blueprint.application.query.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
import com.bone.core.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDetailAssemblerTest {

  @Test
  void fromRowsBuildsNestedDto() {
    OrderWithItemsRow row1 = new OrderWithItemsRow();
    row1.setOrderId(1L);
    row1.setCustomerId(2L);
    row1.setTotalAmount(new BigDecimal("200"));
    row1.setStatus("CREATED");
    row1.setCreatedAt(LocalDateTime.now());
    row1.setItemId(10L);
    row1.setProductId(100L);
    row1.setProductName("A");
    row1.setQuantity(2);
    row1.setUnitPrice(new BigDecimal("100"));
    row1.setSubtotal(new BigDecimal("200"));

    OrderDto dto = OrderDetailAssembler.fromRows(List.of(row1));

    assertEquals(1L, dto.getId());
    assertEquals(1, dto.getItems().size());
    assertEquals(10L, dto.getItems().get(0).getId());
  }

  @Test
  void fromRowsEmptyThrowsNotFound() {
    assertThrows(NotFoundException.class, () -> OrderDetailAssembler.fromRows(List.of()));
  }
}
