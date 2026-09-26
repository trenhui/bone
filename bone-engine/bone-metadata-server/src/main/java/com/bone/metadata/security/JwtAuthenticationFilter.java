package com.bone.metadata.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 自定义 JWT 验证过滤器：拦截 Authorization: Bearer &lt;token&gt; 将 "roles" Claim 转为 SimpleGrantedAuthority
 * 并放入 SecurityContext。
 *
 * <p><b>租户上下文不在此处绑定</b>：租户上下文由 {@code com.bone.metadata.web.TenantContextInterceptor} 在
 * 与安全开关解耦的路径上始终绑定（无论 {@code security.enabled} 是否开启），本过滤器只负责认证。两者职责分离可避免 "security 关闭即无人写租户上下文" 的
 * 500（ADR-0029 失败关闭）。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  public JwtAuthenticationFilter(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        Claims claims = jwtUtil.parseToken(token);
        String username = claims.getSubject();
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        var authorities =
            roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        // G1②：把 userId claim 传入 details，供 CurrentUserProvider 解析（应用角色校验需要账号 ID）。
        // currentOperator() 取 getName()=subject（用户名），不受影响。
        auth.setDetails(claims.get("userId"));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (JwtException ex) {
        // JWT 解析失败时不清除已有的认证信息（可能由 APIKeyFilter 设置）
        // 仅当 SecurityContext 中没有认证时才记录
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
          SecurityContextHolder.clearContext();
        }
      }
    }
    filterChain.doFilter(request, response);
  }
}
