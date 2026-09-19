package com.bone.blueprint.application.query.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.domain.order.projection.OrderWithItemsProjection;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDetailAssemblerTest {

  @Test
  void fromRowsBuildsNestedDto() {
    OrderWithItemsProjection row1 =
        new OrderWithItemsProjection(
            1L,
            2L,
            new BigDecimal("200"),
            "CREATED",
            LocalDateTime.now(),
            10L,
            100L,
            "A",
            2,
            new BigDecimal("100"),
            new BigDecimal("200"));

    OrderDto dto = OrderDetailAssembler.fromRows(List.of(row1));

    assertEquals(1L, dto.getId());
    assertEquals(1, dto.getItems().size());
    assertEquals(10L, dto.getItems().get(0).getId());
  }

  @Test
  void fromRowsEmptyThrowsNotFound() {
    assertEquals(
        404,
        assertThrows(BizException.class, () -> OrderDetailAssembler.fromRows(List.of())).getCode());
  }
}
