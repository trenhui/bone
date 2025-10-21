package com.bone.engine.extension.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置类，配置Spring Security
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护，因为我们使用JWT或其他无状态认证
            .csrf(csrf -> csrf.disable())
            // 设置会话为无状态
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 允许所有API请求访问
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll() // 允许访问H2控制台
                .anyRequest().authenticated()
            )
            // 添加CORS支持
            .cors(Customizer.withDefaults())
            // 允许H2控制台的iframe嵌入
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}