package com.bone.system.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析 {@code Authorization: Bearer <jwt>}，把 {@link JwtPrincipal} 写入
 * Spring Security {@link SecurityContextHolder}；{@code scopes} 转 GrantedAuthority。
 *
 * <p>解析失败时不阻断请求，交由 {@code authorizeHttpRequests} 与方法级 {@code @PreAuthorize}
 * 给出 401/403。这里不绑定 {@code TenantContext}（控制台读侧不做租户过滤；如未来 metrics
 * 需要租户隔离，再仿照 bone-iam 增加 finally clear()）。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenParser parser;
    private final SystemJwtProperties properties;

    public JwtAuthenticationFilter(JwtTokenParser parser, SystemJwtProperties properties) {
        this.parser = parser;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(properties.getHeaderName());
            parser.parse(header).ifPresent(principal -> {
                var granted = principal.scopes().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                var auth = new UsernamePasswordAuthenticationToken(principal, null, granted);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        filterChain.doFilter(request, response);
    }
}
