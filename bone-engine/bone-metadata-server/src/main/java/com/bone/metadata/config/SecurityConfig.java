package com.bone.metadata.config;

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
                              "metadata:template:write")));
    } else {
      http.authorizeHttpRequests(
              auth ->
                  auth.requestMatchers("/api/v1/metadata/health")
                      .permitAll()
                      .requestMatchers("/v1/auth/**")
                      .permitAll()
                      .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .addFilterBefore(
              new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
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
        new SimpleGrantedAuthority("metadata:template:write"));
  }
}
