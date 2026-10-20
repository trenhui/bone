package com.bone.system.domain.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
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
        DomainException.class,
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
        DomainException.class,
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

  // ---------- 三态生命周期（v1.1 §4.5：渲染只看 ACTIVE，写校验放行 ACTIVE+DEPRECATED，只有 DISABLED 拦写） ----------

  @Test
  void lifecycleDeprecateRestoreDisable() {
    SysDictItem item = item();
    assertTrue(item.isActive());
    assertEquals("ACTIVE", item.stateName());

    item.deprecate();
    assertTrue(item.isDeprecated());
    assertEquals("DEPRECATED", item.stateName());

    item.restore();
    assertTrue(item.isActive());

    item.deprecate();
    item.disable();
    assertTrue(item.isDisabled());
    assertEquals("DISABLED", item.stateName());
    // 终态不可直接恢复（防止「删了再说」绕道复活）
    assertThrows(DomainException.class, item::restore);
  }

  @Test
  void illegalLifecycleTransitionsRejected() {
    SysDictItem fresh = item();
    // 生效项不可「恢复」（只能弃用）
    assertThrows(DomainException.class, fresh::restore);
    // 生效项不可直接停用（breaking 变更必须先过渡）
    assertThrows(DomainException.class, fresh::disable);

    SysDictItem deprecated = item();
    deprecated.deprecate();
    // 已弃用项不可再弃用
    assertThrows(DomainException.class, deprecated::deprecate);

    SysDictItem disabled = item();
    disabled.deprecate();
    disabled.disable();
    // 停用终态不可弃用/停用/恢复
    assertThrows(DomainException.class, disabled::deprecate);
    assertThrows(DomainException.class, disabled::disable);
  }

  @Test
  void stateNameFallsBackToActiveForUnknownStatus() {
    SysDictItem item = item();
    item.update(null, null, null, null, null, null, null, null, 7, null); // 越界 status
    assertEquals("ACTIVE", item.stateName());
  }
}
