package com.bone.core.security.auth;

import com.bone.core.security.jwt.JwtPrincipal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 Spring Security 上下文解析当前 JWT 主体。 */
public final class CurrentAccountResolver {

  private CurrentAccountResolver() {}

  public static Optional<JwtPrincipal> currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return Optional.empty();
    Object p = auth.getPrincipal();
    if (p instanceof JwtPrincipal jp) return Optional.of(jp);
    return Optional.empty();
  }

  public static Optional<Long> currentAccountId() {
    return currentPrincipal()
        .flatMap(
            p -> {
              try {
                return Optional.of(Long.parseLong(p.userId()));
              } catch (NumberFormatException ignored) {
                return Optional.empty();
              }
            });
  }
}
