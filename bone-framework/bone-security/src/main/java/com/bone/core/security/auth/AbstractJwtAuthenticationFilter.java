package com.bone.core.security.auth;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 通用 JWT 认证过滤器。解析 Authorization Bearer JWT，写入 Spring Security Context。 各模块可继承后扩展特定行为（如租户绑定、黑名单校验）。
 *
 * <p><b>租户头归一化（安全强约束）</b>：token 校验通过后，请求头 {@code X-Tenant-Id} 的视图会被改写为 token 内
 * <strong>已签名</strong>的 {@code tenantId} claim。因为该头在下游被当作身份使用——{@code bone-web} 的 {@code
 * TenantInterceptor} 用它建立租户上下文，{@code bone-metadata-sdk} 用它做租户数据源路由——若不与 claim 交叉校验， 持有 A 租户 token
 * 的调用方只要改一个请求头就能读写 B 租户数据。
 *
 * <p><b>为何在「头」上做归一化，而不是在下游某个 filter 里 {@code TenantContext.setTenantId} 覆盖</b>： 设置租户上下文的点有三个——模块自有
 * filter（如 {@code TenantContextFilter}，order 常在安全链之前）、 {@code bone-web} 的 MVC 拦截器（在安全链之后）、{@code
 * bone-metadata-sdk} 的数据源路由（读头，不读上下文）。
 * 它们执行顺序不同、各自独立，在框架层改上下文会被更晚执行的一方用原始头覆盖回去；只有在源头把<strong>头</strong> 改对，才对所有消费者同时生效、且与顺序无关。
 *
 * <p>经网关访问时该头本就被 {@code JwtAuthGlobalFilter} 用同一 claim 覆盖，因此这里属<strong>等价改写、无行为变化</strong>；
 * 只有「绕过网关直连模块端口」时才产生差异（伪造值被纠正）。无 token 的请求完全不受影响。
 */
@Slf4j
public abstract class AbstractJwtAuthenticationFilter extends OncePerRequestFilter {

  /**
   * 租户请求头。与 {@code bone-gateway} 的 {@code JwtAuthGlobalFilter}、{@code bone-web} 的 {@code
   * TenantInterceptor} 同名。
   */
  private static final String TENANT_ID_HEADER = "X-Tenant-Id";

  protected final JwtTokenService jwtTokenService;
  protected final JwtConfig jwtConfig;

  public AbstractJwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    this.jwtTokenService = jwtTokenService;
    this.jwtConfig = jwtConfig;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String headerName = jwtConfig.getHeaderName();
    String authHeader = request.getHeader(headerName);

    try {
      if (authHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        String rawToken = jwtTokenService.stripBearerToken(authHeader);

        if (shouldBlockToken(rawToken, request)) {
          log.warn("[JWT] Token 被拦截: uri={}", request.getRequestURI());
          filterChain.doFilter(request, response);
          return;
        }

        Optional<JwtPrincipal> maybePrincipal = jwtTokenService.parse(authHeader);

        if (maybePrincipal.isPresent()) {
          var principal = maybePrincipal.get();
          var granted = principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
          var authentication = new UsernamePasswordAuthenticationToken(principal, null, granted);
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
          onAuthenticated(principal, request);
          // 认证通过后把租户头归一化为已签名 claim（动机与实现取舍见类注释）
          request = normalizeTenantHeader(request, principal);
        } else {
          log.warn("[JWT] Token 解析失败: uri={}", request.getRequestURI());
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      onFinally();
    }
  }

  /** 认证成功后回调，子类可在此绑定租户等上下文。 */
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {}

  /** Token 拦截检查，返回 true 则跳过认证（如黑名单）。 */
  protected boolean shouldBlockToken(String rawToken, HttpServletRequest request) {
    return false;
  }

  /** 请求完成后清理，子类可在此清除线程绑定的上下文。 */
  protected void onFinally() {}

  /**
   * 把 {@code X-Tenant-Id} 的视图归一化为 token 内已签名的 {@code tenantId}。
   *
   * <p>刻意声明为 {@code private}：安全约束不应沿继承链被削弱——若开放覆写，某个子类一次误改即可让全模块的租户防伪失效。
   */
  private HttpServletRequest normalizeTenantHeader(
      HttpServletRequest request, JwtPrincipal principal) {
    String claimTenant = principal.tenantId();
    if (claimTenant == null || claimTenant.isBlank()) {
      // token 未携带租户 claim：不臆测、保持原样（下游 TenantInterceptor 会回落到平台租户）
      return request;
    }
    String headerTenant = request.getHeader(TENANT_ID_HEADER);
    if (claimTenant.equals(headerTenant)) {
      return request; // 网关路径：头本就被同一 claim 覆盖，无需包装
    }
    if (headerTenant != null && !headerTenant.isBlank()) {
      log.warn(
          "[JWT] 请求头 {} = ({}) 与 token 租户 claim = ({}) 不一致，已按 claim 覆盖: uri={}",
          TENANT_ID_HEADER,
          headerTenant,
          claimTenant,
          request.getRequestURI());
    }
    return new TenantHeaderOverridingRequest(request, claimTenant);
  }

  /** 只改写 {@code X-Tenant-Id} 视图、其余全部委托给原请求的包装器。 */
  private static final class TenantHeaderOverridingRequest extends HttpServletRequestWrapper {

    private final String tenantId;

    private TenantHeaderOverridingRequest(HttpServletRequest request, String tenantId) {
      super(request);
      this.tenantId = tenantId;
    }

    @Override
    public String getHeader(String name) {
      return TENANT_ID_HEADER.equalsIgnoreCase(name) ? tenantId : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return TENANT_ID_HEADER.equalsIgnoreCase(name)
          ? Collections.enumeration(List.of(tenantId))
          : super.getHeaders(name);
    }
  }
}
