package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

  // 注意：这里**刻意不再**为 JwtAuthenticationFilter / ReporterTokenFilter 声明
  // FilterRegistrationBean。原因（2026-10-25 联调实测确诊）：
  //   两者都是 OncePerRequestFilter，一旦用 FilterRegistrationBean 以
  //   HIGHEST_PRECEDENCE 挂到 Servlet 容器链上，就会**先于** springSecurityFilterChain 执行并
  //   打上 alreadyFiltered 标记 → 链内 addFilterBefore 进来的同一实例被跳过 →
  //   SecurityContext 永远为空 → permit-unauthenticated=false 时全部端点恒 403
  //   （日志特征：StudioRequestContextFilter 打出 userId=null 的 403，且无 [JWT] 解析失败告警）。
  // 与 bone-masterdata / bone-system 唯一在用的写法保持一致：只 addFilterBefore，
  // 由 Spring Boot 以默认 LOWEST_PRECEDENCE 自动注册，即排在安全链之后，链内实例先生效。

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
    // X-Tenant-Id 必须放行：WebTenantConfiguration 依赖它绑定租户上下文；X-Request-Id：StudioRequestContextFilter
    // 透传链路追踪。
    config.setAllowedHeaders(
        List.of("Content-Type", "Authorization", "X-Tenant-Id", "X-Request-Id"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
