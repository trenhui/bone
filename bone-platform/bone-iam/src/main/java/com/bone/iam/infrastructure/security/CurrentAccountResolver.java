package com.bone.iam.infrastructure.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文解析当前账号；要求 {@link JwtAuthenticationFilter} 已将
 * {@link JwtTokenService.JwtPrincipal} 写入 {@code Authentication.principal}。
 *
 * <p>未登录或不可解析时返回 {@link Optional#empty()}，由调用方决定 401/403 语义。
 */
public final class CurrentAccountResolver {

    private CurrentAccountResolver() {}

    public static Optional<JwtTokenService.JwtPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        Object p = auth.getPrincipal();
        if (p instanceof JwtTokenService.JwtPrincipal jp) {
            return Optional.of(jp);
        }
        return Optional.empty();
    }

    public static Optional<Long> currentAccountId() {
        return currentPrincipal().flatMap(p -> {
            try {
                return Optional.of(Long.parseLong(p.userId()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }
}
