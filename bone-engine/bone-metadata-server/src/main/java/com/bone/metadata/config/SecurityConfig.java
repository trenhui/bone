package com.bone.metadata.config;

import com.bone.metadata.runtime.application.RuntimeAccessGuard;
import com.bone.metadata.security.APIKeyFilter;
import com.bone.metadata.security.JwtAuthenticationFilter;
import com.bone.metadata.security.JwtUtil;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Spring Security 配置 - API Key + JWT 双重认证模式 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  @Value("${security.api-key:}")
  private String apiKey;

  @Value("${security.enabled:false}")
  private boolean securityEnabled;

  private final JwtUtil jwtUtil;

  public SecurityConfig(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // 身份解析与授权开关解耦（与本类注释里「租户上下文与 security.enabled 解耦」同一口径）：
    // JwtAuthenticationFilter 只负责把 token 的 userId 放进 Authentication#details 与 authorities，
    // 不做任何放行/拦截裁决。此前它只注册在 security.enabled=true 分支，导致 dev（默认 false）
    // 下 CurrentUserProvider 永远拿不到 userId —— 前端 bone-metadata-app 的「我的待办/已办」
    // （GET /api/v1/runtime/approval/tasks/pending|decided）无论带不带 JWT 一律 403
    // 「匿名主体没有待办」（2026-10-26 全模块联调实测确诊）。放行策略仍完全由下面的 if/else 决定。
    http.addFilterBefore(
        new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

    if (!securityEnabled) {
      // 安全关闭时：URL 全放行 + 匿名主体携带全部权限，使 @PreAuthorize 通过
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .anonymous(
              anon ->
                  anon.key("anonymous-key")
                      .principal("anonymous")
                      .authorities(
                          AuthorityUtils.createAuthorityList(
                              // G5：model/runtime 二维拆分 + 旧三码 deprecated 别名（2a §4.3）
                              "metadata:read",
                              "metadata:write",
                              "metadata:publish",
                              "metadata:model:read",
                              "metadata:model:write",
                              "metadata:runtime:read",
                              "metadata:runtime:write",
                              "metadata:template:read",
                              "metadata:template:write",
                              // RC-004：实体级权限通配 —— dev 关闭安全时等价于旧粗粒度码（全实体 CRUD）。
                              // 形状统一到 runtime:* 命名空间后，「无实体级权限 → 403」在生产才可能成立：
                              // 生产只需把 wildcard 换发成具体 runtime:{entity}:{op}，无需改一行代码。
                              "runtime:*:read",
                              "runtime:*:write",
                              // 平台管理员：动作级判定（RuntimeAccessGuard#requireAction）对 admin
                              // early-return。dev 匿名主体必须带上它，否则 0034 把动作 permission_code
                              // 换成 runtime:{entity}:{action} 细码后，本地不带 JWT 的直连请求会在
                              // 「具备通配写码、但没有该动作的细码」时被判 403 —— 那正是 §4.6 想达到的
                              // 效果，只是 dev 不该被它挡住。生产开启鉴权后由真实 scopes 决定。
                              RuntimeAccessGuard.PLATFORM_ADMIN)));
    } else {
      http.authorizeHttpRequests(
              auth ->
                  auth.requestMatchers("/api/v1/metadata/health")
                      .permitAll()
                      .requestMatchers("/v1/auth/**")
                      .permitAll()
                      .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                      .permitAll()
                      // Spring Boot 的错误转发端点。**必须放行**：
                      // 认证通过 → 业务抛异常 → FORWARD /error → 不在白名单则被 Security 拦下
                      // → AuthorizationDeniedException → 表现为 401，把真实的
                      // 404/403/500 统统掩盖成"未认证"。2026-10-06 在 bone-file 上实测确诊。
                      .requestMatchers("/error")
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          // JwtAuthenticationFilter 已在两个分支外统一注册（身份解析与授权开关解耦，见方法开头注释）
          .addFilterAfter(
              new APIKeyFilter("X-API-Key", apiKey, getApiKeyAuthorities()),
              JwtAuthenticationFilter.class);
    }
    return http.build();
  }

  private List<GrantedAuthority> getApiKeyAuthorities() {
    // G5：与匿名主体同口径——服务级凭证持有 model/runtime/template 全量 + 旧码别名
    return List.of(
        new SimpleGrantedAuthority("metadata:read"),
        new SimpleGrantedAuthority("metadata:write"),
        new SimpleGrantedAuthority("metadata:publish"),
        new SimpleGrantedAuthority("metadata:model:read"),
        new SimpleGrantedAuthority("metadata:model:write"),
        new SimpleGrantedAuthority("metadata:runtime:read"),
        new SimpleGrantedAuthority("metadata:runtime:write"),
        new SimpleGrantedAuthority("metadata:template:read"),
        new SimpleGrantedAuthority("metadata:template:write"),
        // 与匿名主体同口径：服务级凭证默认持有全实体读写（见上「通配」说明）
        new SimpleGrantedAuthority("runtime:*:read"),
        new SimpleGrantedAuthority("runtime:*:write"),
        // 与匿名主体同口径：服务级凭证需能触发已登记细码的动作（含逃逸动作），见上 PLATFORM_ADMIN 说明
        new SimpleGrantedAuthority(RuntimeAccessGuard.PLATFORM_ADMIN));
  }
}
