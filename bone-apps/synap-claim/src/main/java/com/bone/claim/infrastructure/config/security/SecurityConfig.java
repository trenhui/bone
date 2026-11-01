package com.bone.claim.infrastructure.config.security;

import com.bone.claim.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * synap-claim Spring Security 配置（与平台其它模块同一范式，参考 bone-blueprint 同名类）。
 *
 * <p><b>为什么必须有这个类</b>：模块类路径上有 spring-boot-starter-security（经 {@code bone-security} 传递带入）。一旦应用未声明
 * {@link SecurityFilterChain}，Spring Boot 套用默认策略——HTTP Basic + 表单登录 + 随机密码——表现为「所有接口莫名其妙
 * 401（WWW-Authenticate: Basic）」。2026-10-25 联调实测确诊： 缺此类时网关转发到理赔的请求全部 401，且 {@code @PreAuthorize}
 * 亦不生效。
 *
 * <p><b>策略</b>：无状态（JWT only，无 Session）、关 CSRF（无 Cookie 会话，CSRF 无攻击面）、放行预检/健康探针/错误转发端点，
 * 其余端点要求认证。授权细分交给方法级 {@code @PreAuthorize}（由 {@link EnableMethodSecurity} 开启）：理赔端点声明 {@code
 * claim:claims:read/write}、{@code claim:review:write}、{@code claim:appeal:write}、{@code
 * claim:decision:write}、 {@code claim:settlement:write} 等，scope 由 IAM 签发并随 token 带入（种子见 {@code
 * scripts/migration/0059}）。
 *
 * <p><b>暴露面收敛（B5，最小暴露原则）</b>：{@code /actuator/**} 只放行 {@code /actuator/health**}—— 探针（K8s
 * liveness/readiness、LB 摘流）只需要 health；{@code env/beans/mappings/loggers} 等端点一旦泄露 即是配置与内网拓扑全量外泄，一律
 * 401（确需暴露时经网关侧按角色收敛，不在模块层全网放行）。 Swagger {@code /swagger-ui/** /v3/api-docs/**} 由 {@code
 * bone.claim.apidocs.public}（默认 {@code false}） 控制——接口清单本身就是攻击面测绘图，非开发环境默认不下发。
 *
 * <p><b>/error 必须放行</b>：认证通过 → 业务抛异常 → FORWARD /error → 不在白名单则被 Security 拦下 →
 * AuthorizationDeniedException → 表现为 401，把真实的 404/403/500 统统掩盖成「未认证」（2026-10-06 在 bone-file
 * 上实测确诊的先例）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  /** Swagger/OpenAPI 是否公开。默认 {@code false}：接口清单本身是攻击面测绘图，仅开发环境显式打开。 */
  private static final String APIDOCS_PUBLIC_PROPERTY = "${bone.claim.apidocs.public:false}";

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @org.springframework.beans.factory.annotation.Value(APIDOCS_PUBLIC_PROPERTY)
  private boolean apidocsPublic;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
              // 健康探针公开（K8s/LB 不带凭证）；其余 actuator 端点（env/beans/mappings…）落 anyRequest → 401
              auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
              if (apidocsPublic) {
                auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll();
              }
              // Spring Boot 的错误转发端点，必须放行（动机见类注释）
              auth.requestMatchers("/error").permitAll();
              auth.anyRequest().authenticated();
            })
        .exceptionHandling(
            entryPoint ->
                entryPoint
                    // 缺凭证 / 凭证非法 → 401（Spring Security 6 默认返 403，须显式覆盖）
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    // 已认证但无权限（方法级 @PreAuthorize 拒绝）→ 403
                    .accessDeniedHandler(
                        (request, response, denied) ->
                            response.setStatus(HttpStatus.FORBIDDEN.value())))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
