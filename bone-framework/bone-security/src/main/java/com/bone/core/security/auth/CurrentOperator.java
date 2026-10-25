package com.bone.core.security.auth;

import com.bone.core.security.jwt.JwtPrincipal;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前操作人身份（唯一可信来源）。
 *
 * <p><b>为什么必须有这个类</b>：操作人是 <b>授权与审计的主体</b>，它不是业务参数。一旦允许调用方在请求头 （{@code X-Operator-Id}）或请求体（{@code
 * reviewerId}）里声明自己是谁，任何持有 {@code claim:review:write} 的人都能 「第一次填 A、第二次填
 * B」，把双人复核（职责分离，SoD）变成一句自证——审计日志里留下的是两个从未登录过的名字。
 *
 * <p><b>取值口径</b>：只认服务端已验签的 {@link JwtPrincipal#userId()}， 依次从 Spring Security 上下文、MDC 取（两者同源，均由
 * {@code AbstractJwtAuthenticationFilter} 在验签通过后写入）。 <b>任何客户端输入（请求头、请求体、查询参数）都不参与取值。</b>
 *
 * <p>为什么还要回退 MDC：安全链在请求末期会清空 {@code SecurityContextHolder}，异步/后置处理线程读不到上下文； MDC
 * 从过滤器写入后存活整个请求周期。两者都由同一份已验证 claim 派生，回退不降低可信度。
 */
public final class CurrentOperator {

  /** MDC 中的用户键，与各模块 {@code JwtAuthenticationFilter#onAuthenticated} 写入的键一致。 */
  private static final String MDC_USER_ID = "userId";

  private CurrentOperator() {}

  /** 当前操作人 ID；未认证时为空。 */
  public static Optional<String> id() {
    String fromContext = fromSecurityContext();
    if (fromContext != null) {
      return Optional.of(fromContext);
    }
    String fromMdc = MDC.get(MDC_USER_ID);
    return fromMdc == null || fromMdc.isBlank() ? Optional.empty() : Optional.of(fromMdc);
  }

  /**
   * 当前操作人 ID；未认证直接抛 {@link IllegalStateException}。
   *
   * <p>供已验证鉴权的端点使用（{@code @PreAuthorize} 之后）：拿不到身份属安全链配置错误， 必须响亮失败，<b>绝不能退化为 null
   * 或空串</b>——那会让「操作人」字段在库里静默变空，事后无从追溯。
   */
  public static String requireId() {
    return id().orElseThrow(() -> new IllegalStateException("未认证上下文：无法确定当前操作人（安全链配置异常）"));
  }

  private static String fromSecurityContext() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
      String userId = principal.userId();
      return userId == null || userId.isBlank() ? null : userId;
    }
    return null;
  }
}
