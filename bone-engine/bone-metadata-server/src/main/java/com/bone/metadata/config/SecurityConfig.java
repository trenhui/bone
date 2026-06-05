package com.bone.metadata.config;

import com.bone.metadata.security.APIKeyFilter;
import com.bone.metadata.security.JwtRoleConverter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Spring Security 全局配置 1. JWT 认证（OAuth2 Resource Server） 2. API Key 认证（自定义过滤器） 3. 方法级权限控制 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  @Value("${security.auth-mode:jwt}") // 默认使用JWT
  private String authMode;

  @Value("${security.api-key:}") // API Key配置
  private String apiKey;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());

    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/metadata/health")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated());

    // 动态认证模式
    if ("api-key".equalsIgnoreCase(authMode)) {
      http.addFilterBefore(
          new APIKeyFilter("X-API-Key", apiKey, getApiKeyAuthorities()),
          UsernamePasswordAuthenticationFilter.class);
    } else {
      http.oauth2ResourceServer(
          oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
    }

    return http.build();
  }

  private List<GrantedAuthority> getApiKeyAuthorities() {
    return List.of(
        new SimpleGrantedAuthority("metadata:read"),
        new SimpleGrantedAuthority("metadata:write"),
        new SimpleGrantedAuthority("metadata:publish"));
  }
}
