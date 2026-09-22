package com.bone.iam.domain.model.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link BoneApplication} 纯单测：应用登记创建（默认停用）与部分覆盖更新（无容器）。 */
class BoneApplicationTest {

  @Test
  void testCreateDefaultsToDisabledStatus() {
    BoneApplication app = BoneApplication.create("统一权限", "bone-iam", "IAM 应用", "icon-iam", 100L);

    assertEquals("bone-iam", app.getCode());
    assertEquals(0, app.getStatus());
    assertEquals(100L, app.getTenantId());
  }

  @Test
  void testUpdateOverridesOnlyNonNullFields() {
    BoneApplication app = BoneApplication.create("统一权限", "bone-iam", "IAM 应用", "icon-iam", 100L);

    app.update("新名称", "新描述", null, 1);

    assertEquals("新名称", app.getName());
    assertEquals("新描述", app.getDescription());
    assertEquals("icon-iam", app.getIcon());
    assertEquals(1, app.getStatus());

    app.update(null, "另一描述", "icon-new", null);
    assertEquals("新名称", app.getName());
    assertEquals("icon-new", app.getIcon());
  }
}
