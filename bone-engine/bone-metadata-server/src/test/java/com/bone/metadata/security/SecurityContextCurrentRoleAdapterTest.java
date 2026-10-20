package com.bone.metadata.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.runtime.domain.CurrentRoleProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link SecurityContextCurrentRoleAdapter} 三态语义测试。
 *
 * <p><b>为什么盯三态</b>：{@code null}（内部/受信，engine 不裁剪）与空集合（已认证无角色，engine fail-closed
 * 裁剪）是两种截然不同的安全口径——适配器若把「无 ROLE_ authority」折叠成 null， 无角色用户的受限字段就会被放行（fail-open 退化）。
 */
class SecurityContextCurrentRoleAdapterTest {

  private final CurrentRoleProvider adapter = new SecurityContextCurrentRoleAdapter();

  @BeforeEach
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(String name, String... authorities) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            name,
            null,
            authorities == null
                ? List.of()
                : java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  @DisplayName("ROLE_ 前缀 authority → 去前缀角色码；权限码 authority 被忽略")
  void extractsRoleCodesAndIgnoresPermissionCodes() {
    authenticate("alice", "ROLE_hr", "ROLE_employee", "runtime:exp:read", "platform.admin");
    assertEquals(List.of("hr", "employee"), adapter.currentRoleCodesOrNull());
  }

  @Test
  @DisplayName("已认证但无任何角色（连权限码都没有）→ 空集合，绝不是 null")
  void authenticatedWithoutRoles_returnsEmptyNotNull() {
    authenticate("svc");
    List<String> codes = adapter.currentRoleCodesOrNull();
    assertEquals(List.of(), codes, "空集合 = fail-closed 口径；若返回 null 则受限字段被放行");
  }

  @Test
  @DisplayName("无认证主体 → null（内部/受信语义）")
  void noAuthentication_returnsNull() {
    assertNull(adapter.currentRoleCodesOrNull());
  }

  @Test
  @DisplayName("匿名主体（dev 关闭安全）→ null")
  void anonymous_returnsNull() {
    // AnonymousAuthenticationToken 构造要求非空 authorities（Spring 断言）
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertNull(adapter.currentRoleCodesOrNull());
  }

  @Test
  @DisplayName("空角色码 authority（ROLE_ 后为空）被剔除")
  void blankRoleCodeDropped() {
    authenticate("alice", "ROLE_", "ROLE_hr");
    List<String> codes = adapter.currentRoleCodesOrNull();
    assertEquals(List.of("hr"), codes);
    assertTrue(codes.stream().noneMatch(String::isBlank));
  }
}
