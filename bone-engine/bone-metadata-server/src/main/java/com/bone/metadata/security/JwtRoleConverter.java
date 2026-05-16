package com.bone.metadata.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 将 JWT 中的 "roles" Claim 转为 GrantedAuthority 期望 JWT payload 中有 "roles":
 * ["metadata:read","metadata:write"]
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles == null || roles.isEmpty()) {
      return List.of();
    }
    return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
  }
}
