package com.bone.metadata.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册始终生效的租户上下文拦截器（见 {@link TenantContextInterceptor} 注释：与安全开关解耦）。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final TenantContextInterceptor tenantContextInterceptor;

  public WebMvcConfig(TenantContextInterceptor tenantContextInterceptor) {
    this.tenantContextInterceptor = tenantContextInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tenantContextInterceptor).addPathPatterns("/**");
  }
}
