package com.bone.metadata.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;

/**
 * 自定义 API Key 过滤器：验证请求头中的 X-API-Key
 * 如果校验通过，可将对应用户或角色信息设置到 SecurityContext 中
 */
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
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        String apiKey = request.getHeader(headerName);

        if (StringUtils.isEmpty(apiKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API Key");
            return;
        }

        if (!validApiKey.equals(apiKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "api-key-user",
                null,
                authorities
        );

        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
