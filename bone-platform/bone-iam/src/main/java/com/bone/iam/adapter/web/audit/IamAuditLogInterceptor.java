package com.bone.iam.adapter.web.audit;

import com.bone.iam.application.AuditApplicationService;
import com.bone.iam.application.port.out.CurrentPrincipalPort;
import com.bone.iam.domain.model.audit.valueobject.OperationType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * IAM 审计日志写入拦截器——补齐审计写入侧（此前 {@code recordLog} 无任何调用方，审计页恒空）。
 *
 * <p>记录范围：IAM 域的全部变更类请求（POST/PUT/DELETE）与导出，只读 GET 不记（控制日志量）。 登录/登出分别记为 {@link
 * OperationType#LOGIN}/{@link OperationType#LOGOUT}，登录失败（无主体）也记录。
 *
 * <p><b>参数脱敏</b>：{@code parameters} 只记 HTTP 方法 + URI + 查询串，<b>绝不落请求体</b>（体内容含密码等敏感字段）。
 *
 * <p><b>不干扰业务</b>：审计写入整体 try/catch，任何审计异常只打 WARN，不影响主流程返回值。
 */
@Slf4j
@RequiredArgsConstructor
public class IamAuditLogInterceptor implements HandlerInterceptor {

  private static final String START_ATTR = IamAuditLogInterceptor.class.getName() + ".start";

  /** POST 语义上属于"更新"而非"创建"的子动作路径段。 */
  private static final Set<String> POST_UPDATE_ACTIONS =
      Set.of("enable", "disable", "reset-password", "permissions", "import");

  private final AuditApplicationService auditApplicationService;
  private final CurrentPrincipalPort currentPrincipalPort;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute(START_ATTR, System.currentTimeMillis());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    try {
      OperationType operation = resolveOperation(request);
      if (operation == null) {
        return;
      }
      Long start = (Long) request.getAttribute(START_ATTR);
      Integer duration = start == null ? null : (int) (System.currentTimeMillis() - start);
      Long tenantId =
          currentPrincipalPort.currentPrincipal().map(p -> parseLong(p.tenantId())).orElse(0L);
      Long userId =
          currentPrincipalPort.currentPrincipal().map(p -> parseLong(p.userId())).orElse(null);
      String uri = request.getRequestURI();
      auditApplicationService.recordLog(
          tenantId,
          userId,
          operation,
          extractResourceId(uri),
          extractResourceType(uri),
          request.getRemoteAddr(),
          truncate(request.getHeader("User-Agent")),
          request.getMethod() + " " + uri + queryString(request),
          ex == null ? "SUCCESS" : "FAILED: " + ex.getClass().getSimpleName(),
          duration);
    } catch (Exception auditError) {
      log.warn("[IamAuditLogInterceptor] 审计记录失败（不影响业务）: {}", auditError.getMessage());
    }
  }

  /** 变更类请求 + 导出才记审计；返回 null 表示跳过。 */
  private OperationType resolveOperation(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (!uri.contains("/iam/")) {
      return null;
    }
    String method = request.getMethod();
    String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    String lastSegment = path.substring(path.lastIndexOf('/') + 1);
    if ("GET".equals(method)) {
      return "export".equals(lastSegment) ? OperationType.EXPORT : null;
    }
    if (path.endsWith("/login")) {
      return OperationType.LOGIN;
    }
    if (path.endsWith("/logout")) {
      return OperationType.LOGOUT;
    }
    return switch (method) {
      case "PUT", "PATCH" -> OperationType.UPDATE;
      case "DELETE" -> OperationType.DELETE;
      case "POST" -> POST_UPDATE_ACTIONS.contains(lastSegment)
          ? OperationType.UPDATE
          : OperationType.CREATE;
      default -> null;
    };
  }

  /** 资源类型 = /iam/ 后第一段（如 accounts/roles/depts/menus/tenants）。 */
  private String extractResourceType(String uri) {
    int idx = uri.indexOf("/iam/");
    if (idx < 0) {
      return "iam";
    }
    String rest = uri.substring(idx + 5);
    return rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
  }

  /** 资源 id = 末段且为纯数字时取值（创建类请求此时还没有 id，留空）。 */
  private String extractResourceId(String uri) {
    String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    String lastSegment = path.substring(path.lastIndexOf('/') + 1);
    return lastSegment.matches("\\d+") ? lastSegment : null;
  }

  private static String queryString(HttpServletRequest request) {
    String q = request.getQueryString();
    return q == null ? "" : "?" + q;
  }

  private static String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() > 255 ? value.substring(0, 255) : value;
  }

  private static Long parseLong(String value) {
    try {
      return value == null ? null : Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
