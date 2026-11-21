package com.bone.procurement.infrastructure.config;

import com.bone.core.web.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 采购应用 Web MVC 装配：注册 TenantInterceptor。
 *
 * <p>与 bone-claim / bone-blueprint 同构；缺租户上下文时元数据 SDK 的租户路由与领域仓储无法正确取数。
 *
 * <p>Long/BigInteger → String 由全局 boneLongToStringCustomizer 兜底（本 app 依赖 bone-metadata-sdk，
 * 无 @EnableWebMvc / 自定义 ObjectMapper 覆盖），此处不再重复注册，消除双真源。
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new TenantInterceptor()).addPathPatterns("/**");
  }
}
