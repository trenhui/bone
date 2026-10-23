package com.bone.iam.domain.model.menu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

/** {@link Menu} 纯单测：菜单创建默认值（目录类型/排序）与更新（无容器）。 */
class MenuTest {

  @Test
  void testCreateDefaultsTypeToMenuWithZeroSort() {
    Menu menu =
        Menu.create("用户管理", 0L, "/system/user", "icon-user", null, "system:user:list", null, 1L);

    assertEquals("用户管理", menu.getName());
    assertEquals(0L, menu.getParentId());
    assertEquals("/system/user", menu.getPath());
    assertEquals(0, menu.getOrderNo());
    assertEquals(1, menu.getType());
    assertEquals(1L, menu.getTenantId());
  }

  @Test
  void testUpdateOverridesMenuProfile() {
    Menu menu = Menu.create("用户管理", 0L, "/system/user", "icon-user", 1, "system:user:list", 1, 1L);

    menu.update("用户管理-改", 1L, "/system/user/list", "icon-new", 2, "system:user:add", 2);

    assertEquals("用户管理-改", menu.getName());
    assertEquals(1L, menu.getParentId());
    assertEquals("/system/user/list", menu.getPath());
    assertEquals(2, menu.getOrderNo());
    assertEquals(2, menu.getType());
  }

  /**
   * 缺前导 {@code /} 的 path 必须被拒。
   *
   * <p><b>判据来源</b>：2026-10-23 全模块 UI 巡检实测，菜单树里残留 4 条 {@code path} 形如 {@code E2E<ts>-3} 的 历史 E2E
   * 数据，shell 按 {@code BASE + path} 拼出 {@code http://localhost:3000E2E<ts>-3} ⇒ 浏览器判定非法
   * URL，菜单永远点不开；而菜单树里它与正常菜单毫无区别（典型的「配了不生效」）。 创建与更新两条路径都要拦，否则「先建后改」能绕过。
   */
  @Test
  void testPathWithoutLeadingSlashIsRejectedOnCreateAndUpdate() {
    DomainException createEx =
        assertThrows(
            DomainException.class,
            () -> Menu.create("坏菜单", 0L, "E2E123-3", null, null, null, 1, 1L),
            "缺前导 / 的 path 必须被拒：拼出的 URL 非法，菜单永远点不开");
    assertTrue(
        createEx.getMessage() != null && createEx.getMessage().contains("E2E123-3"),
        "拒绝原因应回显非法 path 值，便于运营定位: " + createEx.getMessage());

    Menu menu = Menu.create("好菜单", 0L, "/iam#/accounts", null, null, null, 1, 1L);
    assertThrows(
        DomainException.class,
        () -> menu.update("好菜单", 0L, "iam#/accounts", null, null, null, 1),
        "更新路径同样要拦，否则「先建合法再改成非法」可绕过");
  }

  /** 目录型节点 path 留空是合法形态（不可导航），不能被校验误伤。 */
  @Test
  void testBlankPathAllowedForDirectoryNode() {
    assertDoesNotThrow(() -> Menu.create("分组", 0L, null, null, null, null, 0, 1L));
    assertDoesNotThrow(() -> Menu.create("分组2", 0L, "   ", null, null, null, 0, 1L));
  }
}
