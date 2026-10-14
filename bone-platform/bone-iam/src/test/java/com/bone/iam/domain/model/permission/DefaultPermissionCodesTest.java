package com.bone.iam.domain.model.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultPermissionCodes} 单元测试——admin 回退路径的权限码完备性。
 *
 * <p><b>为什么断言 {@code runtime:*} 与 {@code platform.admin}</b>（2026-10-13 补齐）：adminFallback 此前只有粗粒度
 * {@code metadata:runtime:read|write}，而 bone-metadata-server 的 {@code RuntimeAccessGuard} 实体级判定只认
 * {@code platform.admin} / {@code runtime:*:{read,write}} / {@code
 * runtime:{entity}:*}}——注解层能过、guard 层全拒， 生产开启鉴权后 admin 访问一切运行时端点 403。dev 匿名主体与 API-Key 主体（{@code
 * SecurityConfig#getApiKeyAuthorities}）本就把这三码列为同口径默认持有，admin 回退路径必须对齐。
 */
class DefaultPermissionCodesTest {

  @Test
  void adminFallback_containsRuntimeEntityLevelCodes() {
    List<String> codes = DefaultPermissionCodes.adminFallback();
    assertTrue(codes.contains("runtime:*:read"), "通配读：全实体读授权面");
    assertTrue(codes.contains("runtime:*:write"), "通配写：全实体写授权面");
    assertTrue(codes.contains("platform.admin"), "平台管理员：数据修复通道 + 行级 ACL 豁免判据");
  }

  @Test
  void adminFallback_hasNoDuplicates() {
    // List.of 不接受重复元素是编译期事实，但增量追加过程中重复会直接抛异常——
    // 单测把「未来有人误加重复码」从启动崩溃前置成一条可读失败。
    List<String> codes = DefaultPermissionCodes.adminFallback();
    Set<String> distinct = Set.copyOf(codes);
    assertEquals(codes.size(), distinct.size(), "adminFallback 不得含重复权限码");
  }

  @Test
  void adminFallback_keepsLegacyCoarseCodes() {
    // 旧三码保留为 deprecated 别名（G5 拆分前的兼容面），与实体级码并存不冲突：
    // 注解层已改动态判据，旧码不再单独放行任何端点，仅服务历史 JWT/种子。
    List<String> codes = DefaultPermissionCodes.adminFallback();
    assertTrue(codes.contains("metadata:read"));
    assertTrue(codes.contains("metadata:write"));
    assertTrue(codes.contains("metadata:runtime:read"));
    assertTrue(codes.contains("metadata:runtime:write"));
  }
}
