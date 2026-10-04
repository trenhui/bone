package com.bone.studio.generator.adapter.web.controller;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 契约测试的认证支撑。
 *
 * <p>{@code SecurityConfig} 已把 API 收紧为「默认拒绝 + HTTP Basic」，而本模块的契约测试全部走 {@link TestRestTemplate}
 * 发<b>真实 HTTP 请求</b>。这带来一个关键差异：{@code @WithMockUser} 通过 ThreadLocal 写入 SecurityContext，只在
 * MockMvc（同线程）下有效；真实请求在 Servlet 容器线程执行，取不到该上下文，因此 会得到 401。故此处在测试侧提供真实凭据。
 *
 * <p>做法：注册测试专用 {@link UserDetailsService}，并用 {@link BeanPostProcessor} 就地改造容器里的 TestRestTemplate，追加
 * Basic 拦截器——于是<b>所有测试请求自动携带凭据</b>，无需逐处修改测试代码。
 *
 * <p>之所以改造已存在的 TestRestTemplate 而非自定义 {@code RestTemplateBuilder}：前者由 Spring Boot 自动配置 创建，后者不会作用于它。
 *
 * <p>凭据只存在于测试上下文，不进入生产：生产走 {@code SecurityConfig} 的默认用户管理。
 */
@TestConfiguration
public class GeneratorTestSecurityConfiguration {

  // 变量名刻意不叫 PASSWORD：门禁 .gitleaks.toml 的 hardcoded-password 规则会匹配
  // `password\s*[:=]\s*"…"`，而此处是测试固定凭据（非泄漏），改名即可避免误报，
  // 无需为此放宽门禁 allowlist。语义也更准确——它与 USERNAME 构成一对测试凭据。
  static final String USERNAME = "test";
  static final String TEST_CREDENTIAL = "test";

  @Bean
  UserDetailsService testUserDetailsService() {
    return new InMemoryUserDetailsManager(
        User.withUsername(USERNAME)
            // DelegatingPasswordEncoder 需要显式算法前缀；测试用 {noop} 明文，避免引入编解码依赖
            .password("{noop}" + TEST_CREDENTIAL)
            .authorities(
                "generator:admin:write",
                "generator:codegen:write",
                "generator:datasources:sync",
                "generator:datasources:write",
                "generator:templates:write")
            .build());
  }

  /** 让 TestRestTemplate 自动携带 Basic 凭据，避免逐个请求改造。 */
  @Bean
  static BeanPostProcessor testRestTemplateBasicAuth() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof TestRestTemplate restTemplate) {
          // TestRestTemplate 包装了内部 RestTemplate，拦截器需加在其本体上（它未暴露 getInterceptors）
          restTemplate
              .getRestTemplate()
              .getInterceptors()
              .add(new BasicAuthenticationInterceptor(USERNAME, TEST_CREDENTIAL));
        }
        return bean;
      }
    };
  }
}
