package com.bone.iam.domain.model.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bone.iam.domain.model.menu.event.MenuCreatedEvent;
import org.junit.jupiter.api.Test;

/** {@link Menu} 纯单测：菜单创建默认值（目录类型/排序）、事件发布与更新（无容器）。 */
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
    assertEquals(1, menu.getDomainEvents().size());
    assertInstanceOf(MenuCreatedEvent.class, menu.getDomainEvents().get(0));
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
}
