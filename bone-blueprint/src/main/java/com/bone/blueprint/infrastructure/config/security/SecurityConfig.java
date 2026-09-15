package com.bone.blueprint.infrastructure.config.security;

import com.bone.blueprint.infrastructure.security.JwtAuthenticationFilter;
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
 * bone-blueprint Spring Security 配置（与平台其它模块同一范式）。
 *
 * <p><b>为什么必须有这个类</b>：模块引入了 {@code spring-boot-starter-security}（经 {@code bone-security}
 * 传递带入）。一旦类路径上有 Spring Security 而应用未声明 {@link SecurityFilterChain}，Spring Boot 就会套用 默认策略—— HTTP
 * Basic + 表单登录 + 启动时打印随机密码——表现为「所有接口莫名其妙 401」。参考样板尤其不能留这个坑： 读者会把 「示例跑不通」当成 DDD 配置写错了。
 *
 * <p><b>策略</b>：无状态（JWT only，无 Session）、关 CSRF（无 Cookie 会话，CSRF 无攻击面）、放行文档与健康检查、 其余端点要求认证。授权细分交给方法级
 * {@code @PreAuthorize}（已由 {@link EnableMethodSecurity} 开启）。
 *
 * <p><b>Token 从哪来</b>：本模块既不签发 token 也不调用 IAM。IAM 用共享密钥 {@code bone.iam.jwt.secret-key} 签发，各模块用
 * {@link com.bone.core.security.jwt.JwtTokenService} 本地离线验签（详见 {@code JwtAuthenticationFilter}）。生产
 * profile 下若仍用默认密钥，{@code JwtConfig} 会拒绝启动。
 *
 * <p><b>租户不在本类处理</b>：租户上下文由 {@code TenantContextFilter} 从 {@code X-Tenant-Id} 建立；生产环境该 请求头由网关
 * {@code JwtAuthGlobalFilter} 用 token 内已签名 claim 覆盖。
 *
 * <p><b>未纳入的差异项</b>：{@code bone-system} 等模块在此处额外配置了面向本仓前端的 CORS 白名单；blueprint 没有配套
 * 前端应用，故不复制该段，以免留下无人维护的常量表。确有浏览器直连需求时再按 {@code bone-system} 的 {@code CorsConfigurationSource} 增补。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
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
