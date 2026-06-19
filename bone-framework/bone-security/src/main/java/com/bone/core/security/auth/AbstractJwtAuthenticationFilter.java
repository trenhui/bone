package com.bone.core.security.auth;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 通用 JWT 认证过滤器。解析 Authorization Bearer JWT，写入 Spring Security Context。
 * 各模块可继承后扩展特定行为（如租户绑定、黑名单校验）。
 */
@Slf4j
public abstract class AbstractJwtAuthenticationFilter extends OncePerRequestFilter {

  protected final JwtTokenService jwtTokenService;
  protected final JwtConfig jwtConfig;

  public AbstractJwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    this.jwtTokenService = jwtTokenService;
    this.jwtConfig = jwtConfig;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String headerName = jwtConfig.getHeaderName();
    String authHeader = request.getHeader(headerName);

    try {
      if (authHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        String rawToken = jwtTokenService.stripBearerToken(authHeader);

        if (shouldBlockToken(rawToken, request)) {
          log.warn("[JWT] Token 被拦截: uri={}", request.getRequestURI());
          filterChain.doFilter(request, response);
          return;
        }

        Optional<JwtPrincipal> maybePrincipal = jwtTokenService.parse(authHeader);

        if (maybePrincipal.isPresent()) {
          var principal = maybePrincipal.get();
          var granted = principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
          var authentication = new UsernamePasswordAuthenticationToken(principal, null, granted);
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
          onAuthenticated(principal, request);
        } else {
          log.warn("[JWT] Token 解析失败: uri={}", request.getRequestURI());
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      onFinally();
    }
  }

  /** 认证成功后回调，子类可在此绑定租户等上下文。 */
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {}

  /** Token 拦截检查，返回 true 则跳过认证（如黑名单）。 */
  protected boolean shouldBlockToken(String rawToken, HttpServletRequest request) {
    return false;
  }

  /** 请求完成后清理，子类可在此清除线程绑定的上下文。 */
  protected void onFinally() {}
}
