package com.bone.masterdata.infrastructure.config;

import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HTTP 入口的租户绑定：读取网关注入的 {@code X-Tenant-Id}（与 bone-gateway 的 JwtAuthGlobalFilter 同一约定）。
 *
 * <p>头缺失时<b>不</b>回落到平台租户 0——那会让写路径静默落到租户 0 且无法察觉；这里保持上下文为空， 由 SDK 的 {@code
 * MissingTenantContextException} 失败关闭。
 */
@Configuration
public class WebTenantConfiguration implements WebMvcConfigurer {

  private static final String TENANT_HEADER = "X-Tenant-Id";

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new TenantContextInterceptor()).addPathPatterns("/**");
  }

  private static final class TenantContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      String tenantId = request.getHeader(TENANT_HEADER);
      if (tenantId != null && !tenantId.isBlank()) {
        TenantContext.setTenantId(tenantId.trim());
      }
      return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
      TenantContext.setTenantId((Long) null);
    }
  }
}
