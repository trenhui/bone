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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Spring Security 配置 - API Key + JWT 双重认证模式 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  @Value("${security.api-key:}")
  private String apiKey;

  private final JwtUtil jwtUtil;

  public SecurityConfig(JwtUtil jwtUtil) {
    this.jwtUtil = jwtUtil;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
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
    return http.build();
  }

  private List<GrantedAuthority> getApiKeyAuthorities() {
    return List.of(
        new SimpleGrantedAuthority("metadata:read"),
        new SimpleGrantedAuthority("metadata:write"),
        new SimpleGrantedAuthority("metadata:publish"));
  }
}
