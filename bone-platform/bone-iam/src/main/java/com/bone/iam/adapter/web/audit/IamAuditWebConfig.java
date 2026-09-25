package com.bone.iam.adapter.web.audit;

import com.bone.iam.application.AuditApplicationService;
import com.bone.iam.application.port.out.CurrentPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * IAM Web MVC 配置——注册审计日志拦截器（详见 {@link IamAuditLogInterceptor}）。
 *
 * <p>拦截全部路径、在拦截器内部按「IAM 域 + 变更类方法」过滤，避免与 API 前缀常量耦合。
 */
@Configuration
@RequiredArgsConstructor
public class IamAuditWebConfig implements WebMvcConfigurer {

  private final AuditApplicationService auditApplicationService;
  private final CurrentPrincipalPort currentPrincipalPort;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new IamAuditLogInterceptor(auditApplicationService, currentPrincipalPort))
        .addPathPatterns("/**");
  }
}
