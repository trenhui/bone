package com.bone.iam.domain.model.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.iam.domain.model.app.vo.AppRole;
import org.junit.jupiter.api.Test;

/** {@link AppPermission} 纯单测：应用权限绑定的创建、角色变更与不变量（无容器）。 */
class AppPermissionTest {

  private static final Long APP_ID = 1001L;
  private static final Long USER_ID = 2002L;
  private static final Long TENANT_ID = 100L;

  @Test
  void testCreateBindsAppUserRoleAndTenant() {
    AppPermission permission = AppPermission.create(APP_ID, USER_ID, AppRole.VIEWER, TENANT_ID);

    assertEquals(APP_ID, permission.getAppId());
    assertEquals(USER_ID, permission.getUserId());
    assertEquals(AppRole.VIEWER, permission.getRole());
    assertEquals(TENANT_ID, permission.getTenantId());
    assertNotNull(permission.getCreatedAt());
    assertNotNull(permission.getUpdatedAt());
  }

  @Test
  void testCreateRejectsNullAppId() {
    assertThrows(
        DomainException.class, () -> AppPermission.create(null, USER_ID, AppRole.ADMIN, TENANT_ID));
  }

  @Test
  void testCreateRejectsNullUserId() {
    assertThrows(
        DomainException.class, () -> AppPermission.create(APP_ID, null, AppRole.ADMIN, TENANT_ID));
  }

  @Test
  void testCreateRejectsNullRole() {
    assertThrows(
        DomainException.class, () -> AppPermission.create(APP_ID, USER_ID, null, TENANT_ID));
  }

  @Test
  void testChangeRoleUpdatesRole() {
    AppPermission permission = AppPermission.create(APP_ID, USER_ID, AppRole.VIEWER, TENANT_ID);

    permission.changeRole(AppRole.ADMIN);

    assertEquals(AppRole.ADMIN, permission.getRole());
  }

  @Test
  void testChangeRoleRejectsNull() {
    AppPermission permission = AppPermission.create(APP_ID, USER_ID, AppRole.VIEWER, TENANT_ID);

    assertThrows(DomainException.class, () -> permission.changeRole(null));
    // 失败不应破坏原有状态
    assertEquals(AppRole.VIEWER, permission.getRole());
  }

  @Test
  void testRoleExternalNameIsLowerCaseAndParsable() {
    assertEquals("admin", AppRole.ADMIN.externalName());
    assertEquals("developer", AppRole.DEVELOPER.externalName());
    assertEquals("viewer", AppRole.VIEWER.externalName());

    assertEquals(AppRole.ADMIN, AppRole.fromExternal("admin"));
    assertEquals(AppRole.DEVELOPER, AppRole.fromExternal("DEVELOPER"));
    assertThrows(DomainException.class, () -> AppRole.fromExternal(null));
    assertThrows(DomainException.class, () -> AppRole.fromExternal("owner"));
  }
}
