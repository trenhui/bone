package com.bone.masterdata.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import org.springframework.stereotype.Component;

/**
 * masterdata 模块 JWT 认证过滤器：仅做认证，不做租户绑定（租户由 {@code
 * com.bone.masterdata.infrastructure.config.WebTenantConfiguration} 读 {@code X-Tenant-Id} 注入， 与
 * bone-system 的口径一致）。
 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }
}
