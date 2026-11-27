package com.bone.procurement.business.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.procurement.business.domain.gateway.BusinessIdentityPort;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextBusinessIdentityPortTest {

  private final BusinessIdentityPort port = new SecurityContextBusinessIdentityPort();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("登录态 JWT 解析租户与操作人")
  void readsJwtIdentity() {
    JwtPrincipal principal = new JwtPrincipal("u-1", "alice", "42", List.of(), List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

    BusinessIdentityPort.Actor actor = port.current();

    assertThat(actor).isNotNull();
    assertThat(actor.tenantId()).isEqualTo(42L);
    assertThat(actor.operator()).isEqualTo("alice");
  }

  @Test
  @DisplayName("匿名与非法租户 fail-closed")
  void anonymousAndInvalidTenantFailClosed() {
    assertThat(port.current()).isNull();

    JwtPrincipal principal = new JwtPrincipal("u-1", "alice", "not-a-number", List.of(), List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

    assertThat(port.current()).isNull();
  }
}
