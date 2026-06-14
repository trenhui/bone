package com.bone.iam.infrastructure.security;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.infrastructure.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析 Authorization Bearer JWT，写入 Spring Security {@link SecurityContextHolder} 与 {@link
 * TenantContext}（来源唯一为 JWT claim {@code tenantId}，详设 §3.4/§4.8）。
 *
 * <p>退出请求前必须清理 {@code TenantContext}，避免线程池复用导致跨租户数据泄漏。
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenService jwtTokenService;
  private final JwtConfig jwtConfig;
  private final TokenBlacklistService tokenBlacklistService;

  public JwtAuthenticationFilter(
      JwtTokenService jwtTokenService,
      JwtConfig jwtConfig,
      TokenBlacklistService tokenBlacklistService) {
    this.jwtTokenService = jwtTokenService;
    this.jwtConfig = jwtConfig;
    this.tokenBlacklistService = tokenBlacklistService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String headerName = jwtConfig.getHeaderName();
    String authHeader = request.getHeader(headerName);
    boolean tenantBound = false;

    try {
      if (authHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        String rawToken = jwtTokenService.stripBearerToken(authHeader);
        if (tokenBlacklistService.isBlacklisted(rawToken)) {
          log.warn("[doFilterInternal] Token 已被加入黑名单: uri={}", request.getRequestURI());
          filterChain.doFilter(request, response);
          return;
        }
        var maybePrincipal = jwtTokenService.parse(authHeader);
        if (maybePrincipal.isPresent()) {
          var principal = maybePrincipal.get();
          var granted = principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
          var authentication = new UsernamePasswordAuthenticationToken(principal, null, granted);
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
          tenantBound = bindTenantContext(principal.tenantId());
        } else {
          log.warn("[doFilterInternal] Token 解析失败（可能已过期）: uri={}", request.getRequestURI());
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      if (tenantBound) {
        TenantContext.clear();
      }
    }
  }

  private boolean bindTenantContext(String tenantClaim) {
    if (tenantClaim == null || tenantClaim.isBlank()) {
      return false;
    }
    try {
      TenantContext.setTenantId(Long.parseLong(tenantClaim));
      return true;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }
}
