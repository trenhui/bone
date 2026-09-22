package com.bone.iam.domain.model.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bone.iam.domain.model.permission.event.PermissionCreatedEvent;
import com.bone.iam.domain.model.permission.vo.PermissionType;
import org.junit.jupiter.api.Test;

/** {@link Permission} 纯单测：权限点创建快照、事件发布与部分覆盖更新（无容器）。 */
class PermissionTest {

  private Permission createPermission() {
    return Permission.create(
        "system:user:add",
        "新增用户",
        "新增用户权限点",
        "USER",
        "/api/v1/users",
        "CREATE",
        0L,
        PermissionType.API,
        1);
  }

  @Test
  void testCreateSnapshotsPermissionProfile() {
    Permission permission = createPermission();

    assertEquals("system:user:add", permission.getCode());
    assertEquals("USER", permission.getResourceType());
    assertEquals("CREATE", permission.getAction());
    assertEquals(PermissionType.API, permission.getType());
    assertEquals(1, permission.getSortOrder());
    assertEquals(1, permission.getDomainEvents().size());
    assertInstanceOf(PermissionCreatedEvent.class, permission.getDomainEvents().get(0));
  }

  @Test
  void testUpdateOverridesNonNullFieldsOnly() {
    Permission permission = createPermission();

    permission.update("新增用户-改", "新描述", "USER", null, "READ", 2L, PermissionType.BUTTON, 3);

    assertEquals("新增用户-改", permission.getName());
    // resourcePath 传 null 不覆盖
    assertEquals("/api/v1/users", permission.getResourcePath());
    assertEquals("READ", permission.getAction());
    assertEquals(PermissionType.BUTTON, permission.getType());
    assertEquals(3, permission.getSortOrder());
  }
}
