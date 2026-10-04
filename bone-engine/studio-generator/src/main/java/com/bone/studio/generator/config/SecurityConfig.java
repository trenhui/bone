package com.bone.studio.generator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 代码生成器 API 的安全配置。
 *
 * <p>此前本模块引入了 {@code spring-boot-starter-security} 却没有 SecurityConfig，Spring Boot 的默认安全配置
 * 因此接管：所有请求被重定向到登录页（302 + HTML），使 API 完全不可用，且错误语义错误——契约要求的 404 / 405 全部变成 302，客户端无法据此处理。
 *
 * <p>此处显式声明本模块的契约：生成器 API 为内部工具，按既有测试契约开放访问；同时禁用 formLogin / httpBasic 并使用无状态会话，保证未认证请求得到 401 而非
 * 302 重定向。
 *
 * <p><b>安全提示</b>：{@code anyRequest().permitAll()} 意味着本服务所有端点均无需凭据即可调用。若部署形态 需要认证，应在此收紧为 {@code
 * authenticated()} 或按权限码逐项声明——本类集中承载该策略，便于单点调整。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
}
