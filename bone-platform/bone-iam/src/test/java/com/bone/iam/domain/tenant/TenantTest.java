package com.bone.iam.domain.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link Tenant} 纯单测：租户创建（默认启用）、启停、资料与配额更新（无容器）。 */
class TenantTest {

  private Tenant createTenant() {
    return Tenant.create(1L, "示范租户", "demo", 1, "admin@demo.com");
  }

  @Test
  void testCreateDefaultsToEnabledAndKeepsId() {
    Tenant tenant = createTenant();

    // 子类 setId override 生效，避免字段遮蔽
    assertEquals(1L, tenant.getId());
    assertEquals("demo", tenant.getCode());
    assertEquals(1, tenant.getStatus());
    assertNull(tenant.getMaxAccounts());
    assertNull(tenant.getMaxRoles());
  }

  @Test
  void testUpdateOverridesOnlyNonNullFields() {
    Tenant tenant = createTenant();

    tenant.update("示范租户-改", null, 2);

    assertEquals("示范租户-改", tenant.getName());
    assertEquals("admin@demo.com", tenant.getAdminEmail());
    assertEquals(2, tenant.getLevel());
  }

  @Test
  void testEnableDisableFlipsStatus() {
    Tenant tenant = createTenant();

    tenant.disable();
    assertEquals(0, tenant.getStatus());

    tenant.enable();
    assertEquals(1, tenant.getStatus());
  }

  @Test
  void testUpdateQuotaChangesLimits() {
    Tenant tenant = createTenant();

    tenant.updateQuota(100, 50);

    assertEquals(100, tenant.getMaxAccounts());
    assertEquals(50, tenant.getMaxRoles());

    // null 表示不限制
    tenant.updateQuota(null, null);
    assertNull(tenant.getMaxAccounts());
  }
}
