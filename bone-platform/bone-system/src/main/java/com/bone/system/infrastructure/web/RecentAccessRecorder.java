package com.bone.system.infrastructure.web;

import com.bone.core.security.auth.CurrentAccountResolver;
import com.bone.system.domain.model.console.RecentAccess;
import com.bone.system.domain.repository.RecentAccessRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 控制台最近访问录制器（S-13：落地详设 §7 预留的 {@code cnsl_recent_access} 孤儿表）。
 *
 * <p>以 {@link HandlerInterceptor#postHandle} 在 {@code /api/v1/console/**} 端点成功返回后追加一条 {@link
 * RecentAccess}（鉴权失败 / 异常不会触发 postHandle，天然不录）。 录制为非关键副作用：解析不到主体或落库失败仅告警，不影响主响应。
 *
 * <p>轮询（如 bone-shell 30s 拉 overview）会产生高频重复写入，故对「同一租户 + 用户 + 资源」 做 60s
 * 内存冷却（多实例各持一份，属尽力而为的限流，不保证全局去重）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecentAccessRecorder implements HandlerInterceptor {

  private static final String RESOURCE_TYPE = "console";
  private static final Duration COOLDOWN = Duration.ofSeconds(60);
  private static final Map<String, Long> LAST_RECORDED = new ConcurrentHashMap<>();

  private final RecentAccessRepository recentAccessRepository;

  @Override
  public void postHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      ModelAndView modelAndView) {
    if (!"GET".equals(request.getMethod())) {
      return;
    }
    var principalOpt = CurrentAccountResolver.currentPrincipal();
    if (principalOpt.isEmpty()) {
      return;
    }
    var principal = principalOpt.get();
    Long userId;
    try {
      userId = Long.parseLong(principal.userId());
    } catch (NumberFormatException e) {
      return;
    }
    Long tenantId;
    try {
      tenantId =
          (principal.tenantId() == null || principal.tenantId().isBlank())
              ? 0L
              : Long.parseLong(principal.tenantId());
    } catch (NumberFormatException e) {
      tenantId = 0L;
    }

    String resourceId = request.getRequestURI();
    String key = tenantId + ":" + userId + ":" + resourceId;
    long now = Instant.now().toEpochMilli();
    Long last = LAST_RECORDED.get(key);
    if (last != null && now - last < COOLDOWN.toMillis()) {
      return;
    }
    LAST_RECORDED.put(key, now);

    String resourceName = resolveResourceName(resourceId);
    String accessUrl = request.getRequestURL().toString();
    try {
      recentAccessRepository.save(
          RecentAccess.of(tenantId, userId, RESOURCE_TYPE, resourceId, resourceName, accessUrl));
    } catch (Exception e) {
      log.warn("[RecentAccess] 录制最近访问失败 uri={} userId={}", resourceId, userId, e);
    }
  }

  private String resolveResourceName(String uri) {
    if (uri == null) {
      return "console";
    }
    String tail = uri.substring(uri.lastIndexOf('/') + 1);
    return switch (tail) {
      case "overview" -> "系统概览";
      case "services" -> "服务状态";
      case "resources" -> "资源使用";
      case "metrics" -> "关键指标";
      case "quick-actions" -> "快捷操作";
      default -> tail.isBlank() ? "console" : tail;
    };
  }
}
