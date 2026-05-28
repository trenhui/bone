package com.bone.system.infrastructure.security;

import java.util.List;

/**
 * JWT 主体（与 {@code bone-iam} {@code JwtTokenService.JwtPrincipal} 结构一致）。
 *
 * <p>字段 {@code scopes} 即 IAM 颁发的权限码列表（如 {@code sys:console:read}），由
 * {@link JwtAuthenticationFilter} 转换为 Spring Security {@code SimpleGrantedAuthority}，
 * 用于方法级 {@code @PreAuthorize("hasAuthority('sys:console:read')")} 校验。
 */
public record JwtPrincipal(String userId, String username, String tenantId, List<String> scopes) {}
