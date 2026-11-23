package com.bone.procurement.sourcing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.tenant.context.TenantContext;
import com.bone.procurement.sourcing.domain.gateway.AwardIdentityPort.AwardIdentity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** 授标身份解析：匿名返回 null，已认证返回 JWT 登录态租户与具名操作人。 */
class AwardIdentityPortTest {

  private final SecurityContextAwardIdentityPort port = new SecurityContextAwardIdentityPort();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void 匿名返回null() {
    SecurityContextHolder.clearContext();
    assertThat(port.current()).isNull();
  }

  @Test
  void 已认证但租户不可解析且无可信上下文时返回null() {
    JwtPrincipal principal =
        new JwtPrincipal(
            "u-1", "alice", "not-a-number", List.of("procurement:rfx:approve"), List.of());
    var granted = principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, granted));
    TenantContext.clear();

    assertThat(port.current()).isNull();
  }

  @Test
  void 已认证返回登录态租户与操作人() {
    JwtPrincipal principal =
        new JwtPrincipal("u-1", "alice", "1", List.of("procurement:rfx:approve"), List.of());
    var granted = principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, granted));

    AwardIdentity identity = port.current();

    assertThat(identity).isNotNull();
    assertThat(identity.tenantId()).isEqualTo(1L);
    assertThat(identity.operator()).isEqualTo("alice");
  }
}
