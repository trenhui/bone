package com.bone.system.domain.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.system.domain.model.dict.SysDictItem;
import com.bone.system.domain.model.dict.enums.DictTagType;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 字典项聚合纯单测（无容器、无 IO）。 */
class SysDictItemTest {

  private static SysDictItem item() {
    return SysDictItem.create(
        1L,
        0L,
        DictCode.typeCode("biz_region"),
        DictCode.of("GD"),
        "广东省",
        "440000",
        null,
        DictTagType.SUCCESS,
        null,
        "CN-GD",
        null,
        null,
        false,
        null,
        null,
        null);
  }

  @Test
  void createAppliesDefaultsAndKeepsExternalCode() {
    SysDictItem item = item();
    assertEquals(0, item.getSort());
    assertEquals(1, item.getStatus());
    assertEquals(DictTagType.SUCCESS, item.tagType());
    assertEquals("CN-GD", item.getExternalCode());
    assertFalse(item.isDefaultItem());
    assertTrue(item.isEnabled());
    assertTrue(item.isEffectiveNow());
  }

  @Test
  void labelIsRequired() {
    assertThrows(
        BizException.class,
        () ->
            SysDictItem.create(
                2L,
                0L,
                DictCode.typeCode("biz_region"),
                DictCode.of("GD"),
                "  ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null));
  }

  @Test
  void effectiveRangeMustBeOrdered() {
    LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
    assertThrows(
        BizException.class,
        () ->
            SysDictItem.create(
                3L,
                0L,
                DictCode.typeCode("biz_region"),
                DictCode.of("GD"),
                "广东省",
                null,
                null,
                null,
                null,
                null,
                from,
                to,
                false,
                null,
                null,
                null));
  }

  @Test
  void effectiveWindowIsEvaluatedAtInstant() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 12, 31, 23, 59);
    SysDictItem item =
        SysDictItem.create(
            4L,
            0L,
            DictCode.typeCode("biz_region"),
            DictCode.of("GD"),
            "广东省",
            null,
            null,
            null,
            null,
            null,
            from,
            to,
            false,
            null,
            null,
            null);
    assertTrue(item.isEffectiveAt(LocalDateTime.of(2026, 6, 1, 0, 0)));
    assertFalse(item.isEffectiveAt(LocalDateTime.of(2025, 6, 1, 0, 0)));
    assertTrue(item.isExpired(LocalDateTime.of(2027, 1, 1, 0, 0)));
    assertTrue(item.isPending(LocalDateTime.of(2025, 1, 1, 0, 0)));
  }

  @Test
  void updateKeepsBusinessKey() {
    SysDictItem item = item();
    item.update(
        "广东省（改）",
        "440000-new",
        DictTagType.WARNING,
        "dict.region.gd",
        "CN-GD-2",
        null,
        null,
        5,
        0,
        "备注");
    assertEquals("广东省（改）", item.getLabel());
    assertEquals("440000-new", item.getValue());
    assertEquals(DictTagType.WARNING, item.tagType());
    assertEquals("CN-GD-2", item.getExternalCode());
    assertEquals(5, item.getSort());
    assertEquals(0, item.getStatus());
    // 业务键不受 update 影响
    assertEquals("GD", item.getCode());
    assertEquals("biz_region", item.getTypeCode());
  }

  @Test
  void defaultFlagToggle() {
    SysDictItem item = item();
    item.markAsDefault();
    assertTrue(item.isDefaultItem());
    item.clearDefault();
    assertFalse(item.isDefaultItem());
  }

  @Test
  void unknownTagTypeFallsBackToDefault() {
    SysDictItem item =
        SysDictItem.create(
            5L,
            0L,
            DictCode.typeCode("biz_region"),
            DictCode.of("GD"),
            "广东省",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null);
    assertEquals(DictTagType.DEFAULT, item.tagType());
  }
}
