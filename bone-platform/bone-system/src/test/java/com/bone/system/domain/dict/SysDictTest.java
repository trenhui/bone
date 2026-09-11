package com.bone.system.domain.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.BizException;
import com.bone.system.domain.dict.vo.DictType;
import org.junit.jupiter.api.Test;

/** {@link SysDict} 纯单测：字典项默认排序与状态、更新覆盖语义、类型约束（无容器）。 */
class SysDictTest {

  @Test
  void testCreateAppliesDefaultSortAndStatus() {
    SysDict dict =
        SysDict.create(1L, DictType.of("order_status"), "订单状态", "PENDING", "待支付", "0", null, null);

    assertEquals(0, dict.getSort());
    assertEquals(1, dict.getStatus());
    assertNotNull(dict.getCreatedAt());
  }

  @Test
  void testCreateKeepsExplicitSortAndStatus() {
    SysDict dict =
        SysDict.create(2L, DictType.of("order_status"), "订单状态", "PAID", "已支付", "1", 5, 1);

    assertEquals(5, dict.getSort());
    assertEquals(1, dict.getStatus());
  }

  @Test
  void testUpdateOverridesEntryFields() {
    SysDict dict =
        SysDict.create(3L, DictType.of("order_status"), "订单状态", "CANCELLED", "已取消", "2", 0, 1);

    dict.update("订单状态", "已关闭", "3", 6, 0);

    assertEquals("已关闭", dict.getLabel());
    assertEquals("3", dict.getValue());
    assertEquals(6, dict.getSort());
    assertEquals(0, dict.getStatus());
  }

  @Test
  void testBlankOrOversizedTypeRejected() {
    assertThrows(BizException.class, () -> DictType.of(null));
    assertThrows(BizException.class, () -> DictType.of("  "));
    assertThrows(BizException.class, () -> DictType.of("t".repeat(65)));
  }
}
