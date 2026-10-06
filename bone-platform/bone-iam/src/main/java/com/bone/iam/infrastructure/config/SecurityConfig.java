package com.bone.iam.infrastructure.config;

import com.bone.core.model.ApiResponse;
import com.bone.iam.infrastructure.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;

  @Value("${bone.cors.allowed-origins:http://localhost:3000,http://localhost:3003}")
  private String allowedOrigins;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.objectMapper = objectMapper;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    // 2026-10-04 移除 "/api/v1/apps/**"（P0-4）：
                    // 该前缀下有 8 个写端点（App 的create/update/delete + grant/revokePermission
                    // + Module 的增删改）。permitAll 只作用于 AuthorizationFilter，
                    // @PreAuthorize 走 MethodInterceptor 仍生效——所以当时"看起来安全"。
                    // 但它把安全边界从"一条规则"退化为"每个方法各写一次注解"：
                    // 此后任何人新增端点漏写 @PreAuthorize（SystemController 自己记录过这种真实漏法），
                    // 别的前缀漏注解只是"任何登录用户可写"，**这个前缀漏注解是"任何人可写"**，
                    // 爆炸半径差一个量级。
                    // 实测依据：AppController 全部读端点已挂 iam:apps:read、写端点已挂 iam:apps:write，
                    // ModuleController 写端点已挂 iam:apps:write；前端 16 处调用全部在已登录的
                    // iam-app / metadata-app 内（经 createApiClient 带 token），登录页不依赖本前缀。
                    // ⇒ 收敛后无行为回归，且把"漏注解"的爆炸半径降回与其他前缀一致。
                    .requestMatchers(
                        "/api/v1/iam/login",
                        "/api/v1/iam/sso/callback",
                        "/api/v1/iam/sso/config",
                        "/.well-known/jwks.json",
                        // 健康检查供网关/容器探针免鉴权调用；其余 actuator 端点仍在鉴权之后
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        // Spring Boot 的错误转发端点。**必须放行**：
                        // 认证通过 → 业务抛异常 → FORWARD /error → 不在白名单则被 Security 拦下
                        // → AuthorizationDeniedException → 表现为 401，把真实的
                        // 404/403/500 统统掩盖成"未认证"。2026-10-06 在 bone-file 上实测确诊。
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
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
                        }))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins = List.of(allowedOrigins.split(",")).stream().map(String::trim).toList();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Request-Id"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
