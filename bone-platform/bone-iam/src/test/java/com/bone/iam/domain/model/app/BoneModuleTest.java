package com.bone.iam.domain.model.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link BoneModule} 纯单测：应用模块登记创建（默认停用/默认排序）与部分覆盖更新（无容器）。 */
class BoneModuleTest {

  @Test
  void testCreateDefaultsToDisabledWithZeroSort() {
    BoneModule mod = BoneModule.create(5L, "用户模块", "user", "用户与权限", 100L);

    assertEquals(5L, mod.getAppId());
    assertEquals("user", mod.getCode());
    assertEquals(0, mod.getStatus());
    assertEquals(0, mod.getSortOrder());
    assertEquals(100L, mod.getTenantId());
  }

  @Test
  void testUpdateOverridesOnlyNonNullFields() {
    BoneModule mod = BoneModule.create(5L, "用户模块", "user", "用户与权限", 100L);

    mod.update("账号模块", "账号管理", 1);

    assertEquals("账号模块", mod.getName());
    assertEquals(1, mod.getStatus());

    mod.update(null, null, 0);
    assertEquals("账号模块", mod.getName());
    assertEquals("账号管理", mod.getDescription());
    assertEquals(0, mod.getStatus());
  }
}
