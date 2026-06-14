package com.bone.metadata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 自定义 API Key 过滤器：验证请求头中的 X-API-Key 如果校验通过，可将对应用户或角色信息设置到 SecurityContext 中 */
public class APIKeyFilter extends OncePerRequestFilter {

  private final String headerName;
  private final String validApiKey;
  private final List<GrantedAuthority> authorities;

  public APIKeyFilter(String headerName, String validApiKey, List<GrantedAuthority> authorities) {
    this.headerName = headerName;
    this.validApiKey = validApiKey;
    this.authorities = authorities;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    // 如果已有认证（由 JwtAuthenticationFilter 设置），直接放行
    if (SecurityContextHolder.getContext().getAuthentication() != null
        && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
      chain.doFilter(request, response);
      return;
    }

    String apiKey = request.getHeader(headerName);

    if (StringUtils.hasText(apiKey)) {
      if (validApiKey.equals(apiKey)) {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("api-key-user", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } else {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key");
        return;
      }
    }

    chain.doFilter(request, response);
  }
}
