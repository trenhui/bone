package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final boolean permitUnauthenticated;
  private final ReporterTokenFilter reporterTokenFilter;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      ReporterTokenFilter reporterTokenFilter,
      @Value("${bone.extension.studio.security.permit-unauthenticated:false}")
          boolean permitUnauthenticated) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.reporterTokenFilter = reporterTokenFilter;
    this.permitUnauthenticated = permitUnauthenticated;
  }

  /**
   * Spring Security 6 要求：凡参与 SecurityFilterChain 的 Filter bean，都必须带有已注册的 order，否则创建 filterChain
   * 时直接抛 {@code The Filter class ... does not have a registered order}，上下文启动失败（本模块 以 web
   * 环境启动即命中）。注意 {@code @Order} 注解不满足该校验——它读取的是 {@code FilterRegistrationBean} 上的
   * order，故须在此显式注册。order 与链内顺序一致：ReporterTokenFilter 挂在 JwtAuthenticationFilter 之前，故取值更小。
   */
  @Bean
  public FilterRegistrationBean<ReporterTokenFilter> reporterTokenFilterRegistration() {
    FilterRegistrationBean<ReporterTokenFilter> registration =
        new FilterRegistrationBean<>(reporterTokenFilter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration() {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(jwtAuthenticationFilter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize -> {
              authorize
                  .requestMatchers(HttpMethod.OPTIONS, "/**")
                  .permitAll()
                  .requestMatchers("/actuator/**", "/h2-console/**")
                  .permitAll()
                  .requestMatchers(
                      "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**")
                  .permitAll()
                  // Spring Boot 的错误转发端点。**必须放行**：
                  // 认证通过 → 业务抛异常 → FORWARD /error → 不在白名单则被 Security 拦下
                  // → AuthorizationDeniedException → 表现为 401，把真实的
                  // 404/403/500 统统掩盖成"未认证"。2026-10-06 在 bone-file 上实测确诊。
                  .requestMatchers("/error")
                  .permitAll();
              if (permitUnauthenticated) {
                // 联调放行：白名单路径需与 Controller 映射一致（/api/v1/extension 单数）
                authorize
                    .requestMatchers("/api/v1/extension/**")
                    .permitAll()
                    .requestMatchers("/api/v1/marketplace/**")
                    .permitAll()
                    .requestMatchers("/api/v1/deployment-status/**")
                    .permitAll();
              }
              // 数据面执行日志上报端点：业务进程（bone-extension-sdk StudioExecutionLogReporter）
              // 以进程身份异步上报，不携带终端用户 JWT——业界控制面对数据面上报通道单独放行，
              // 但**必须由 ReporterTokenFilter 校验机器身份**（X-Reporter-Token 共享密钥）：
              // 裸permitAll 等于任何能访问该端口的人都能伪造执行日志（className/status/errorMessage
              // 全部取自请求体），污染运维视图与告警依据。放行位置独立于联调开关，鉴权模式同样生效。
              authorize
                  .requestMatchers(
                      HttpMethod.POST,
                      "/api/v1/extension/execution-logs:ingest",
                      "/api/v1/extension/execution-logs/ingest")
                  .permitAll();
              authorize.requestMatchers("/api/**").authenticated().anyRequest().permitAll();
            })
        .headers(headers -> headers.frameOptions(frame -> frame.disable()))
        // 两者均以内置的 UsernamePasswordAuthenticationFilter 作锚点：Spring Security 6 解析锚点 order 时只认
        // 其内置注册表，用自定义 filter 当锚点（原先 reporter 挂在 JwtAuthenticationFilter.class 之前）会抛
        // “The Filter class ... does not have a registered order”。同 order 下按插入顺序稳定排序，故先插
        // reporter（须先于 JWT 校验机器身份），再插 jwt，链内相对顺序与原先一致。
        .addFilterBefore(reporterTokenFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3002",
            "http://localhost:3003",
            "http://localhost:3004",
            "http://localhost:3005",
            "http://localhost:3006",
            "http://localhost:3007",
            "http://localhost:3008"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
