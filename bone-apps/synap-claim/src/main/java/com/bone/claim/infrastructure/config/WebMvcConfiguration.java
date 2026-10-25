package com.bone.claim.infrastructure.config;

import com.bone.core.web.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * synap-claim Web MVC 配置（与 bone-blueprint 同范式）。
 *
 * <p>注册 {@link TenantInterceptor}：从（已被 {@code AbstractJwtAuthenticationFilter} 归一化为 token 已签名 claim
 * 的）{@code X-Tenant-Id} 头建立 {@code com.bone.core.tenant.context.TenantContext}——理赔领域 {@code
 * TenantPortAdapter#currentTenantId()} 与 bone-metadata-sdk 的租户数据源路由都消费该上下文。 拦截器 fail-closed：头缺失即抛
 * {@code MissingTenantContextException}（ADR-0029），不回落到平台租户 0。
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new TenantInterceptor()).addPathPatterns("/**");
  }
}
