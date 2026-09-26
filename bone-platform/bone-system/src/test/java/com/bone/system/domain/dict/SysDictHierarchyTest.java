package com.bone.system.domain.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.system.domain.model.dict.SysDictHierarchy;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import org.junit.jupiter.api.Test;

/** 层级关系聚合纯单测（无容器、无 IO）。 */
class SysDictHierarchyTest {

  private static SysDictHierarchy node(
      String code, String parentCode, Integer parentLevel, String parentPath) {
    return SysDictHierarchy.create(
        1L,
        0L,
        DictCode.typeCode("biz_region"),
        null,
        DictCode.of(code),
        parentCode,
        parentLevel,
        parentPath,
        0);
  }

  @Test
  void rootNodePathAndLevel() {
    SysDictHierarchy root = node("GD", null, null, null);
    assertEquals("/GD/", root.getPath());
    assertEquals(1, root.getLevel());
    assertTrue(root.isRoot());
    assertTrue(root.isDefaultView());
    assertEquals(SysDictHierarchy.DEFAULT_HIERARCHY, root.getHierarchyCode());
  }

  @Test
  void childNodeDerivesPathAndLevelFromParent() {
    SysDictHierarchy child = node("GZ", "GD", 1, "/GD/");
    assertEquals("/GD/GZ/", child.getPath());
    assertEquals(2, child.getLevel());
    assertFalse(child.isRoot());
  }

  @Test
  void pathOfAlwaysEndsWithSeparator() {
    assertEquals("/A/", SysDictHierarchy.pathOf(null, "A"));
    assertEquals("/A/B/", SysDictHierarchy.pathOf("/A", "B"));
    // 前后分隔符是精确包含判断的前提：/GZ 不能被 /GZX/ 命中
    assertFalse("/GZX/".contains("/GZ" + "/"));
  }

  @Test
  void hasAncestorDetectsCycle() {
    SysDictHierarchy gz = node("GZ", "GD", 1, "/GD/");
    SysDictHierarchy ns =
        SysDictHierarchy.create(
            2L,
            0L,
            DictCode.typeCode("biz_region"),
            null,
            DictCode.of("NS"),
            "GZ",
            gz.getLevel(),
            gz.getPath(),
            0);
    assertTrue(ns.hasAncestor("GD"));
    assertTrue(ns.hasAncestor("GZ"));
    assertFalse(ns.hasAncestor("ZJ"));
  }

  @Test
  void moveToRootResetsPathAndLevel() {
    SysDictHierarchy node = node("GZ", "GD", 1, "/GD/");
    node.moveTo(null, null, null, 3);
    assertTrue(node.isRoot());
    assertEquals("/GZ/", node.getPath());
    assertEquals(1, node.getLevel());
    assertEquals(3, node.getSort());
  }

  @Test
  void hierarchyCodeNormalizesBlankToDefault() {
    SysDictHierarchy node =
        SysDictHierarchy.create(
            3L, 0L, DictCode.typeCode("biz_region"), "  ", DictCode.of("GD"), null, null, null, 0);
    assertEquals(SysDictHierarchy.DEFAULT_HIERARCHY, node.getHierarchyCode());
    assertEquals("REPORT_TREE", SysDictHierarchy.normalizeHierarchyCode(" REPORT_TREE "));
  }

  @Test
  void tooLongHierarchyCodeIsRejected() {
    String tooLong = "H".repeat(65);
    assertThrows(
        BizException.class,
        () ->
            SysDictHierarchy.create(
                4L,
                0L,
                DictCode.typeCode("biz_region"),
                tooLong,
                DictCode.of("GD"),
                null,
                null,
                null,
                0));
  }
}
