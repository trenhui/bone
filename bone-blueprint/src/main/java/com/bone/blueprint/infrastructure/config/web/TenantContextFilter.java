package com.bone.blueprint.infrastructure.config.web;

import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从请求头 {@code X-Tenant-Id} 注入租户上下文（开发/演示用）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                TenantContext.setTenantId(Long.parseLong(tenantHeader.trim()));
            } catch (NumberFormatException ignored) {
                // 非法头忽略，走默认租户 0
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
