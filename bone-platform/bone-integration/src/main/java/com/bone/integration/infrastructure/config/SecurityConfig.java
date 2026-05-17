package com.bone.integration.infrastructure.config;

import com.bone.integration.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 平台集成服务安全配置。
 *
 * <p>开发默认 {@code bone.integration.security.jwt-enabled=false} 放行 {@code /v1/integration/**}；
 * 生产设置 {@code BONE_INTEGRATION_JWT_ENABLED=true} 与 IAM 共用 Bearer Token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final IntegrationSecurityProperties securityProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            IntegrationSecurityProperties securityProperties, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.securityProperties = securityProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityProperties.isJwtEnabled()) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**")
                            .permitAll()
                            .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
                            .permitAll()
                            .requestMatchers("/v1/integration/**")
                            .authenticated()
                            .anyRequest()
                            .permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/v1/integration/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated());
        }
        return http.build();
    }
}
