package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.domain.gateway.ReporterTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 数据面上报通道的机器身份校验（共享密钥）。
 *
 * <p>只作用于两个 ingest 路径 —— {@code POST /api/v1/extension/execution-logs:ingest} 与其 斜杠别名。上报方（{@code
 * bone-extension-sdk}的 {@code StudioExecutionLogReporter}）是**业务进程**， 不持有终端用户 JWT，所以 {@code
 * SecurityConfig} 只能对它 {@code permitAll()}。 但 permitAll 的代价是**匿名可写**：请求体的 className / status /
 * errorMessage 全部可控， 任何能访问该端口的人都能伪造执行日志，污染 overview 看板与告警依据。
 *
 * <p><b>业界标准做法</b>：控制面用用户权限码，数据面用机器身份（共享密钥 / mTLS / 网关 ACL）。 本过滤器是三者中成本最低的一种，且不要求上报方持有任何用户凭证。
 *
 * <p><b>失败关闭</b>：令牌不匹配直接 401 + JSON 错误体，不进业务方法 —— 不存在 "校验失败但仍写库"的中间态。 服务端未配置密钥且 {@code
 * reporter-auth-required=true}（默认）时返回 <b>503</b> 而非放行：
 * 让"部署漏配密钥"表现为上报功能不可用（业务方立刻发现日志没上报），而不是执行日志被匿名伪造。
 *
 * <p><b>503 与 401 必须分开</b>：前者是服务端部署问题（上报方补令牌无用，要去改部署配置）， 后者是上报方身份问题（去查自己的密钥）。合成同一个码会让排障方向完全搞反。
 *
 * <p>比对用 {@link java.security.MessageDigest#isEqual}（常量时间）而非 {@code String#equals}，
 * 避免按字节短路比较带来的时序侧信道。
 */
/**
 * 上报令牌校验过滤器，挂在 JWT 认证之前（见 SecurityConfig 的 addFilterBefore）。
 *
 * <p>{@code @Order} 为必需：Spring Security 6 会对参与 SecurityFilterChain 的 Filter bean 校验是否已注册
 * order，未注册时直接抛 “The Filter class ... does not have a registered order” 导致上下文启动失败。order 取值小于
 * JwtAuthenticationFilter，与链内先后一致。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ReporterTokenFilter extends OncePerRequestFilter {

  static final String INGEST_PATH_COLON = "/api/v1/extension/execution-logs:ingest";
  static final String INGEST_PATH_SLASH = "/api/v1/extension/execution-logs/ingest";
  static final String HEADER = "X-Reporter-Token";

  /** 服务端未配置机器身份密钥（部署问题）。 */
  static final String CODE_NOT_CONFIGURED = "EXTENSION_REPORTER_TOKEN_NOT_CONFIGURED";

  /** 已配置密钥但令牌缺失或不匹配（上报方身份问题）。 */
  static final String CODE_INVALID = "EXTENSION_REPORTER_TOKEN_INVALID";

  private static final Set<String> GUARDED_PATHS = Set.of(INGEST_PATH_COLON, INGEST_PATH_SLASH);

  private final ReporterTokenValidator validator;

  public ReporterTokenFilter(ReporterTokenValidator validator) {
    this.validator = validator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!isIngestRequest(request)) {
      chain.doFilter(request, response);
      return;
    }
    if (validator.isAcceptable(request.getHeader(HEADER))) {
      chain.doFilter(request, response);
      return;
    }
    // 失败关闭：服务端缺配置 ⇒ 503（部署问题）；令牌不符 ⇒ 401（上报方身份问题）。
    boolean serverMisconfigured = !validator.isConfigured();
    writeError(
        response,
        serverMisconfigured ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED,
        serverMisconfigured ? CODE_NOT_CONFIGURED : CODE_INVALID,
        serverMisconfigured ? "数据面上报未启用：服务端未配置上报令牌" : "数据面上报令牌校验失败");
  }

  private void writeError(
      HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
  }

  private boolean isIngestRequest(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    return path != null && GUARDED_PATHS.contains(path);
  }
}
