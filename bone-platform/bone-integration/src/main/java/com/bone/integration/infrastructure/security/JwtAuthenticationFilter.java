package com.bone.integration.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import org.springframework.stereotype.Component;

/** 集成服务 JWT 认证过滤器。继承框架抽象类，复用统一的 Token 解析与身份载体。 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }
}
