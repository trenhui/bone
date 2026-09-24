package com.bone.iam.domain.model.role;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link Role} 纯单测：角色创建快照与描述更新（无容器）。 */
class RoleTest {

  @Test
  void testCreateSnapshotsRoleProfile() {
    Role role = Role.create("超级管理员", "admin", "内置角色", 1, 1L, 0L);

    assertEquals("超级管理员", role.getName());
    assertEquals("admin", role.getCode());
    assertEquals(1, role.getType());
    assertEquals(1L, role.getTenantId());
    assertEquals(0L, role.getParentRoleId());
  }

  @Test
  void testUpdateChangesDescription() {
    Role role = Role.create("超级管理员", "admin", "内置角色", 1, 1L, 0L);

    role.update("平台内置的超级管理员角色");

    assertEquals("平台内置的超级管理员角色", role.getDescription());
  }
}
