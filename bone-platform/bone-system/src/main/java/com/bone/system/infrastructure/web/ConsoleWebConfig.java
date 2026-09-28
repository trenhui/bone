package com.bone.system.infrastructure.web;

import com.bone.core.web.PlatformApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 控制台 Web 装配：注册 {@link RecentAccessRecorder}，仅拦截 {@code /api/v1/console/**}（S-13 落地详情见录制器）。 */
@Configuration
@RequiredArgsConstructor
public class ConsoleWebConfig implements WebMvcConfigurer {

  private final RecentAccessRecorder recentAccessRecorder;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(recentAccessRecorder)
        .addPathPatterns(PlatformApiPaths.CONSOLE_V1 + "/**");
  }
}
