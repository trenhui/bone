package com.bone.blueprint.infrastructure.config.web;

import com.bone.core.web.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * <p>配置拦截器、跨域等Web相关设置
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  /**
   * 添加拦截器
   *
   * @param registry 拦截器注册表
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 添加租户拦截器
    registry.addInterceptor(new TenantInterceptor()).addPathPatterns("/**");
  }
}
