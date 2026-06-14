package com.bone.engine.extension.studio.security;

import com.bone.engine.extension.studio.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenService jwtTokenService;
  private final JwtConfig jwtConfig;

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    this.jwtTokenService = jwtTokenService;
    this.jwtConfig = jwtConfig;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader(jwtConfig.getHeaderName());

    if (authHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      jwtTokenService
          .parse(authHeader)
          .ifPresent(
              principal -> {
                if (StringUtils.hasText(principal.userId())) {
                  MDC.put("userId", principal.userId());
                }
                if (StringUtils.hasText(principal.tenantId())) {
                  MDC.put("tenantId", principal.tenantId());
                }
                var authorities =
                    principal.scopes().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
                var authentication =
                    new UsernamePasswordAuthenticationToken(
                        principal.username(), null, authorities);
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
              });
    }

    filterChain.doFilter(request, response);
  }
}
