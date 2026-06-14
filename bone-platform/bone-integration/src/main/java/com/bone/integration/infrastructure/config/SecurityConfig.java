package com.bone.integration.infrastructure.config;

import com.bone.core.model.ApiResponse;
import com.bone.integration.infrastructure.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * 平台集成服务安全配置。
 *
 * <p>开发默认 {@code bone.integration.security.jwt-enabled=false} 放行 {@code /v1/integration/**}； 生产设置
 * {@code BONE_INTEGRATION_JWT_ENABLED=true} 与 IAM 共用 Bearer Token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final IntegrationSecurityProperties securityProperties;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;

  public SecurityConfig(
      IntegrationSecurityProperties securityProperties,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      ObjectMapper objectMapper) {
    this.securityProperties = securityProperties;
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.objectMapper = objectMapper;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    if (securityProperties.isJwtEnabled()) {
      // 使用 AntPathRequestMatcher 替代默认的 MvcRequestMatcher，
      // 避免 Spring Security 6.2.x 在 context-path 下路径匹配失败
      http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers(new AntPathRequestMatcher("/**", "OPTIONS"))
                      .permitAll()
                      .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/**"))
                      .permitAll()
                      .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui/**"))
                      .permitAll()
                      .requestMatchers(AntPathRequestMatcher.antMatcher("/v3/api-docs/**"))
                      .permitAll()
                      .requestMatchers(AntPathRequestMatcher.antMatcher("/v1/integration/**"))
                      .authenticated()
                      .anyRequest()
                      .authenticated());
    } else {
      http.authorizeHttpRequests(
          auth ->
              auth.requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/**"))
                  .permitAll()
                  .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui/**"))
                  .permitAll()
                  .requestMatchers(AntPathRequestMatcher.antMatcher("/v3/api-docs/**"))
                  .permitAll()
                  .requestMatchers(AntPathRequestMatcher.antMatcher("/v1/integration/**"))
                  .permitAll()
                  .anyRequest()
                  .permitAll());
    }

    http.exceptionHandling(
        ex ->
            ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response.setCharacterEncoding("UTF-8");
                      ApiResponse<?> body = ApiResponse.error(401, "未登录或 Token 已过期");
                      response.getWriter().write(objectMapper.writeValueAsString(body));
                    })
                .accessDeniedHandler(
                    (request, response, accessDeniedException) -> {
                      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response.setCharacterEncoding("UTF-8");
                      ApiResponse<?> body = ApiResponse.error(403, "无权限访问该资源");
                      response.getWriter().write(objectMapper.writeValueAsString(body));
                    }));

    return http.build();
  }
}
