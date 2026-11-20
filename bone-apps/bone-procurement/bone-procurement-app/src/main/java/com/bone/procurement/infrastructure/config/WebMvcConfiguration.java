package com.bone.procurement.infrastructure.config;

import com.bone.core.web.interceptor.TenantInterceptor;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 采购应用 Web MVC 装配：注册 TenantInterceptor，并把雪花 Long 序列化为字符串（防前端精度截断）。
 *
 * <p>与 bone-claim / bone-blueprint 同构；缺租户上下文时元数据 SDK 的租户路由与领域仓储无法正确取数。
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new TenantInterceptor()).addPathPatterns("/**");
  }

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    Module module = longIdAsStringModule();
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
        jackson.getObjectMapper().registerModule(module);
      }
    }
  }

  private Module longIdAsStringModule() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(Long.class, ToStringSerializer.instance);
    module.addSerializer(Long.TYPE, ToStringSerializer.instance);
    return module;
  }
}
