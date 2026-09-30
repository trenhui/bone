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

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      @Value("${bone.extension.studio.security.permit-unauthenticated:false}")
          boolean permitUnauthenticated) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.permitUnauthenticated = permitUnauthenticated;
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
              // 安全边界由网络层（内网/网关白名单）保障。放行位置独立于联调开关，鉴权模式同样生效。
              authorize
                  .requestMatchers(
                      HttpMethod.POST,
                      "/api/v1/extension/execution-logs:ingest",
                      "/api/v1/extension/execution-logs/ingest")
                  .permitAll();
              authorize.requestMatchers("/api/**").authenticated().anyRequest().permitAll();
            })
        .headers(headers -> headers.frameOptions(frame -> frame.disable()))
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
