package com.bone.system.domain.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.system.domain.model.dict.SysDictType;
import com.bone.system.domain.model.dict.enums.DictCategory;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import org.junit.jupiter.api.Test;

/** 字典类型聚合纯单测（无容器、无 IO）。 */
class SysDictTypeTest {

  @Test
  void createAppliesDefaultsAndKeepsTenant() {
    SysDictType type =
        SysDictType.create(
            1L,
            0L,
            DictCode.typeCode("sys_status"),
            "系统状态",
            DictCategory.LIST,
            "system",
            null,
            null,
            null,
            false,
            null,
            null);
    assertEquals(0, type.getSort());
    assertEquals(1, type.getStatus());
    assertEquals(DictCategory.LIST, type.category());
    assertEquals(0L, type.getTenantId());
    assertFalse(type.isBuiltin());
    assertTrue(type.isEnabled());
  }

  @Test
  void enumCategoryRequiresEnumClass() {
    assertThrows(
        BizException.class,
        () ->
            SysDictType.create(
                2L,
                0L,
                DictCode.typeCode("order_status"),
                "订单状态",
                DictCategory.ENUM,
                "system",
                null,
                null,
                null,
                false,
                null,
                null));
  }

  @Test
  void nonEnumCategoryRejectsEnumClass() {
    assertThrows(
        BizException.class,
        () ->
            SysDictType.create(
                3L,
                0L,
                DictCode.typeCode("biz_region"),
                "区域",
                DictCategory.LIST,
                "system",
                "com.bone.blueprint.OrderStatus",
                null,
                null,
                false,
                null,
                null));
  }

  @Test
  void maxDepthOnlyAllowedForCascade() {
    assertThrows(
        BizException.class,
        () ->
            SysDictType.create(
                4L,
                0L,
                DictCode.typeCode("biz_region"),
                "区域",
                DictCategory.LIST,
                "system",
                null,
                3,
                null,
                false,
                null,
                null));

    SysDictType cascade =
        SysDictType.create(
            5L,
            0L,
            DictCode.typeCode("biz_region"),
            "区域",
            DictCategory.CASCADE,
            "system",
            null,
            3,
            null,
            false,
            null,
            null);
    assertEquals(3, cascade.getMaxDepth());
    assertTrue(cascade.category().isCascade());
  }

  @Test
  void builtinTypeCannotBeDeleted() {
    SysDictType type =
        SysDictType.create(
            6L,
            0L,
            DictCode.typeCode("sys_yes_no"),
            "是否",
            DictCategory.LIST,
            "system",
            null,
            null,
            null,
            true,
            null,
            null);
    assertTrue(type.isBuiltin());
    assertThrows(BizException.class, type::assertDeletable);
  }

  @Test
  void updateOperationalKeepsSourceConfig() {
    SysDictType type =
        SysDictType.create(
            7L,
            0L,
            DictCode.typeCode("order_status"),
            "订单状态",
            DictCategory.ENUM,
            "system",
            "com.bone.blueprint.OrderStatus",
            null,
            null,
            true,
            null,
            null);
    type.updateOperational("订单状态（改）", 9, 0, "说明");
    assertEquals("订单状态（改）", type.getName());
    assertEquals(9, type.getSort());
    assertEquals(0, type.getStatus());
    // 内置类型走运营态更新，真源配置（枚举类）与编码不受影响
    assertEquals("com.bone.blueprint.OrderStatus", type.getEnumClass());
    assertEquals("order_status", type.getCode());
  }

  @Test
  void readOnlyTypeRejectsTenantEdit() {
    SysDictType type =
        SysDictType.create(
            8L,
            0L,
            DictCode.typeCode("iso_currency"),
            "币种",
            DictCategory.LIST,
            "system",
            null,
            null,
            null,
            false,
            null,
            null);
    type.updateOperational("币种", null, null, null);
    // editable 默认 1；置 0 后租户改项应被拒
    assertFalse(type.getEditable() != null && type.getEditable() == 0);
  }

  @Test
  void valueFormatIsValidatedAgainstTypeAndRegex() {
    SysDictType type =
        SysDictType.create(
            9L,
            0L,
            DictCode.typeCode("biz_amount"),
            "金额档位",
            DictCategory.LIST,
            "system",
            null,
            null,
            null,
            false,
            null,
            null);
    type.applyValueFormat("DECIMAL", "^\\d+(\\.\\d{1,2})?$", null);

    type.assertValueFormat("12.50");
    assertThrows(BizException.class, () -> type.assertValueFormat("abc"));
    assertThrows(BizException.class, () -> type.assertValueFormat("12.345"));
  }

  @Test
  void codeSegmentsDeriveParentByPrefix() {
    SysDictType type =
        SysDictType.create(
            10L,
            0L,
            DictCode.typeCode("biz_region"),
            "行政区域",
            DictCategory.CASCADE,
            "system",
            null,
            3,
            null,
            false,
            null,
            null);
    type.applyValueFormat("STRING", null, "2,2,2");

    assertEquals(java.util.List.of(2, 2, 2), type.segments());
    assertEquals("440000", type.deriveParentCode("440100").orElse(null));
    assertEquals("440300", type.deriveParentCode("440305").orElse(null));
    // 一级代码（末后各段全零）没有父级
    assertTrue(type.deriveParentCode("440000").isEmpty());
    // 长度不匹配分段总长 → 不推导，交由人工点选父级
    assertTrue(type.deriveParentCode("4401").isEmpty());
  }

  @Test
  void illegalCodeSegmentsAreRejected() {
    SysDictType type =
        SysDictType.create(
            11L,
            0L,
            DictCode.typeCode("biz_region"),
            "行政区域",
            DictCategory.CASCADE,
            "system",
            null,
            3,
            null,
            false,
            null,
            null);
    assertThrows(BizException.class, () -> type.applyValueFormat(null, null, "2,x,2"));
  }
}
