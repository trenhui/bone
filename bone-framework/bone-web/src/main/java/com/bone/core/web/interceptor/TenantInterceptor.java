package com.bone.core.web.interceptor;

import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器
 *
 * <p>从请求头提取租户信息，并设置到租户上下文中 支持扩展点机制，如果存在BizContext类，会同步设置到业务上下文
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

  private static final String TENANT_ID_HEADER = "X-Tenant-Id";
  private static final String BIZ_CODE_HEADER = "X-Biz-Code";
  private static final String DEFAULT_BIZ_CODE = "DEFAULT";

  private static final boolean BIZ_CONTEXT_AVAILABLE = isBizContextAvailable();

  /**
   * 请求预处理，提取租户信息并设置到上下文。
   *
   * <p><b>fail-closed</b>：请求头缺失 {@code X-Tenant-Id} 时<b>不</b>回落到平台租户 {@code 0}——那会让写路径 静默落到租户 0
   * 且无法察觉，或让读路径静默退化为全租户读。上下文保持为空， 由 SDK 的 {@code
   * MissingTenantContextException}（ADR-0029）失败关闭。该分支只对应越权/畸形入站，生产环境由网关在每个请求上盖章 {@code
   * X-Tenant-Id}，不会触发。
   *
   * <p>与各业务模块的 {@code WebTenantConfiguration}（masterdata / integration / generator /
   * metadata-server）保持同一约定。
   *
   * @param request HTTP请求
   * @param response HTTP响应
   * @param handler 处理器
   * @return 是否继续执行
   */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 从请求头获取租户ID（缺失即留空 → SDK 失败关闭，不兜底 0）
    String tenantId = request.getHeader(TENANT_ID_HEADER);
    if (tenantId != null && !tenantId.isBlank()) {
      tenantId = tenantId.trim();
      // 设置到租户上下文（支持线程池传递）
      TenantContext.setTenantId(tenantId);

      // 从请求头获取业务码（仅在有租户上下文时同步）
      String bizCode = request.getHeader(BIZ_CODE_HEADER);
      if (bizCode == null || bizCode.isBlank()) {
        bizCode = DEFAULT_BIZ_CODE;
      }
      // 同步到业务上下文（如果可用）
      if (BIZ_CONTEXT_AVAILABLE) {
        setBizContext(tenantId, bizCode);
      }
      log.debug(
          "租户上下文已设置: tenantId={}, bizCode={}, uri={}", tenantId, bizCode, request.getRequestURI());
    } else {
      log.debug("未提供租户ID，上下文保持空（fail-closed）: uri={}", request.getRequestURI());
    }
    return true;
  }

  /**
   * 请求完成后清理上下文
   *
   * @param request HTTP请求
   * @param response HTTP响应
   * @param handler 处理器
   * @param ex 异常
   */
  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    // 清理租户上下文，防止内存泄漏
    TenantContext.clear();
    log.debug("租户上下文已清理: uri={}", request.getRequestURI());
  }

  /**
   * 检查BizContext类是否可用
   *
   * @return 是否可用
   */
  private static boolean isBizContextAvailable() {
    try {
      Class.forName("com.bone.engine.extension.support.context.BizContext");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * 设置业务上下文
   *
   * @param tenantId 租户ID
   * @param bizCode 业务码
   */
  private void setBizContext(String tenantId, String bizCode) {
    try {
      Class<?> bizContextClass =
          Class.forName("com.bone.engine.extension.support.context.BizContext");
      Object builder = bizContextClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("tenant", String.class).invoke(builder, tenantId);
      builder.getClass().getMethod("bizCode", String.class).invoke(builder, bizCode);
      builder.getClass().getMethod("build").invoke(builder);
    } catch (Exception e) {
      log.debug("设置业务上下文失败，忽略: {}", e.getMessage());
    }
  }
}
