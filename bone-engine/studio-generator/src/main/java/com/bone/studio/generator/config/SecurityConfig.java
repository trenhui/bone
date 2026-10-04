package com.bone.studio.generator.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 代码生成器 API 的安全配置。
 *
 * <p><b>背景</b>：本模块引入了 {@code spring-boot-starter-security}（控制器上的 {@code @PreAuthorize} 需要它） 却一直没有
 * SecurityConfig，Spring Boot 的默认安全配置因此接管——所有请求被重定向到登录页（302 + HTML）， API 实质不可用，且契约要求的 404 / 405
 * 全部退化为 302、响应体不再是 ApiResponse 信封。
 *
 * <p><b>本类的取舍</b>：控制器上早已声明 {@code @PreAuthorize}（细粒度权限码，如 {@code generator:templates:write}），但因缺少
 * {@code @EnableMethodSecurity} 而一直未生效。此处一并补齐，使既有授权 声明真正生效——这比让端点裸奔更贴近模块本来的设计意图：
 *
 * <ul>
 *   <li>启用方法级鉴权（{@code @EnableMethodSecurity}），使既有 {@code @PreAuthorize} 生效；
 *   <li>整体要求已认证（{@code anyRequest().authenticated()}），默认拒绝；
 *   <li>禁用 formLogin / httpBasic，未认证时返回 <b>401 JSON</b> 而非 302 重定向到登录页（API 语义正确）；
 *   <li>使用无状态会话。
 * </ul>
 *
 * <p>本模块业务代码不读取任何 principal（无 SecurityContext 依赖），故认证只作访问控制用途。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        // 启用 HTTP Basic：真实 HTTP 契约测试（TestRestTemplate）经 withBasicAuth() 携带凭据。
        // @WithMockUser 仅对 MockMvc 生效——它写入 ThreadLocal，而真实请求在 Servlet 容器线程执行，取不到。
        .httpBasic(basic -> basic.authenticationEntryPoint(unauthorizedEntryPoint()))
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(eh -> eh.authenticationEntryPoint(unauthorizedEntryPoint()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated());
    return http.build();
  }

  /** 与统一响应约定（ApiResponse）保持一致的 401 信封，避免调用方拿到 HTML。 */
  private static final String UNAUTHORIZED_BODY =
      "{\"success\":false,\"code\":\"AUTH_UNAUTHORIZED\",\"message\":\"未认证或缺少访问凭据\"}";

  /**
   * 未认证时返回 401 JSON 信封，而非 Spring Security 默认的登录页重定向（302）或 WWW-Authenticate 挑战。 httpBasic
   * 与全局异常处理共用此入口，保证两种认证入口的响应一致，且调用方拿到的始终是 ApiResponse 结构。
   */
  private static AuthenticationEntryPoint unauthorizedEntryPoint() {
    return (request, response, ex) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write(UNAUTHORIZED_BODY);
    };
  }
}
