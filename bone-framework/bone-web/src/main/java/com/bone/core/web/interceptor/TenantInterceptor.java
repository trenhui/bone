package com.bone.core.web.interceptor;

import com.bone.core.context.RequestContextLifecycle;
import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 租户与请求上下文拦截器。
 *
 * <p>从请求头提取租户信息（{@code X-Tenant-Id}）与业务编码（{@code X-Biz-Code}），设置到 {@link TenantContext} 中； 并通过
 * {@link RequestContextLifecycle} SPI 驱动上层引擎（如扩展引擎）建立业务上下文。
 *
 * <p><b>生命周期安全保证</b>：
 *
 * <ul>
 *   <li><b>入口防御性清理</b>：{@code preHandle} 开始前清理历史 ThreadLocal，杜绝 Servlet 线程池复用脏数据；
 *   <li><b>异步请求释放</b>：实现 {@link AsyncHandlerInterceptor#afterConcurrentHandlingStarted}，在异步分派时立即释放原
 *       Servlet 线程上下文；
 *   <li><b>异常隔离与必须清理</b>：{@code clearContext} 内部以 {@code try-finally} 包装，确保即便外部 SPI 清理抛错，核心 {@link
 *       TenantContext} 也必定被清理；
 *   <li><b>fail-closed</b>：请求头缺失 {@code X-Tenant-Id} 时上下文保持为空，交由持久化 SDK 失败关闭。
 * </ul>
 */
@Slf4j
@Component
public class TenantInterceptor implements AsyncHandlerInterceptor {

  private static final String TENANT_ID_HEADER = "X-Tenant-Id";
  private static final String BIZ_CODE_HEADER = "X-Biz-Code";
  private static final String DEFAULT_BIZ_CODE = "DEFAULT";

  private final List<RequestContextLifecycle> lifecycles;

  public TenantInterceptor(ObjectProvider<RequestContextLifecycle> lifecycleProvider) {
    this.lifecycles =
        lifecycleProvider != null
            ? lifecycleProvider.orderedStream().toList()
            : Collections.emptyList();
  }

  /** 保留无参构造器，确保测试与手写 new TenantInterceptor() 场景完全向后兼容 */
  public TenantInterceptor() {
    this.lifecycles = Collections.emptyList();
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 1. 入口防御性清理：防止 Servlet 线程池复用带入历史脏数据
    clearContext();

    // 2. 从请求头获取租户ID（缺失即留空 → SDK 失败关闭，不兜底 0）
    String tenantId = request.getHeader(TENANT_ID_HEADER);
    if (tenantId != null && !tenantId.isBlank()) {
      tenantId = tenantId.trim();
      try {
        TenantContext.setTenantId(tenantId);

        // 从请求头获取业务码（仅在有租户上下文时同步）
        String bizCode = request.getHeader(BIZ_CODE_HEADER);
        if (bizCode == null || bizCode.isBlank()) {
          bizCode = DEFAULT_BIZ_CODE;
        } else {
          bizCode = bizCode.trim();
        }

        // 3. 驱动扩展上下文生命周期初始化
        for (RequestContextLifecycle lifecycle : lifecycles) {
          lifecycle.initialize(tenantId, bizCode);
        }
        log.debug(
            "租户与业务上下文已设置: tenantId={}, bizCode={}, uri={}",
            tenantId,
            bizCode,
            request.getRequestURI());
      } catch (RuntimeException e) {
        // 初始化失败必须就地清理并拒绝：Spring 的 applyPreHandle 在 preHandle 抛异常时不会回调本拦截器的
        // afterCompletion，若此处不清理，ThreadLocal 将泄漏并污染线程池后续请求。不捕获 Error，不静默降级。
        clearContext();
        log.error("请求上下文初始化失败，已拒绝并清理: uri={}", request.getRequestURI(), e);
        throw e;
      }
    } else {
      log.debug("未提供租户ID，上下文保持空（fail-closed）: uri={}", request.getRequestURI());
    }
    return true;
  }

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 4. 异步请求处理开始：原 Servlet 线程将被放回线程池，必须立即释放当前线程上下文
    clearContext();
    log.debug("异步请求开始处理，已释放当前 Servlet 线程上下文: uri={}", request.getRequestURI());
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    // 5. 请求完成清理
    clearContext();
    log.debug("请求完成，租户与业务上下文已清理: uri={}", request.getRequestURI());
  }

  /** 幂等清理操作。确保即便外部 SPI 清理发生异常，核心 TenantContext 也必定在 finally 中被清理。 */
  private void clearContext() {
    try {
      for (RequestContextLifecycle lifecycle : lifecycles) {
        try {
          lifecycle.clear();
        } catch (Throwable t) {
          log.error(
              "Failed to clear RequestContextLifecycle: {}", lifecycle.getClass().getName(), t);
        }
      }
    } finally {
      TenantContext.clear();
    }
  }
}
