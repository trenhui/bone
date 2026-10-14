package com.bone.metadata.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 将 JWT 中的权限码 claim 转为 GrantedAuthority。
 *
 * <p><b>claim 键名双读</b>（2026-10-13 与 {@link JwtAuthenticationFilter} 同口径修复）： {@code roles} 优先（旧形状
 * token）、{@code scopes} 兜底（IAM 现行签发形状——{@code bone-security/JwtTokenService} 只写 {@code scopes}）、
 * 双缺为空集合。原先只读 {@code roles} 且无 null 保护，遇到 IAM token 直接 NPE / 静默空权限集。
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles == null || roles.isEmpty()) {
      roles = jwt.getClaimAsStringList("scopes");
    }
    if (roles == null || roles.isEmpty()) {
      return List.of();
    }
    return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
  }
}
