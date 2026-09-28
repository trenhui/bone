package com.bone.system.domain.model.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link RecentAccess} 纯单测：工厂产出与字段约束（无容器，R8：聚合须同名纯单测）。 */
class RecentAccessTest {

  @Test
  void ofSetsTenantUserAndResource() {
    RecentAccess access =
        RecentAccess.of(
            7L, 42L, "console", "/api/v1/console/overview", "系统概览", "http://x/overview");

    assertEquals(7L, access.getTenantId());
    assertEquals(42L, access.getUserId());
    assertEquals("console", access.getResourceType());
    assertEquals("/api/v1/console/overview", access.getResourceId());
    assertEquals("系统概览", access.getResourceName());
    assertEquals("http://x/overview", access.getAccessUrl());
  }

  @Test
  void ofCapsNullTenantToZero() {
    RecentAccess access =
        RecentAccess.of(null, 1L, "console", "/api/v1/console/metrics", "关键指标", "u");

    assertEquals(0L, access.getTenantId());
  }

  @Test
  void ofStampsAccessedAt() {
    RecentAccess access =
        RecentAccess.of(1L, 1L, "console", "/api/v1/console/services", "服务状态", "u");

    assertNotNull(access.getAccessedAt());
    assertTrue(access.getAccessedAt() instanceof LocalDateTime);
  }

  @Test
  void idIsNullBeforePersist() {
    RecentAccess access =
        RecentAccess.of(1L, 1L, "console", "/api/v1/console/resources", "资源使用", "u");

    assertNull(access.getId());
  }
}
