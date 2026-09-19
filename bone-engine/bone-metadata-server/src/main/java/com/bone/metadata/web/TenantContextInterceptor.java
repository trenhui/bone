package com.bone.metadata.web;

import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 入站租户上下文拦截器（与 bone-web {@code TenantInterceptor} 同约定）。
 *
 * <p><b>为什么必须有、且要独立于 Security</b>：ADR-0029 下 SDK 对租户表的读 / 改 / 删是<strong>失败关闭</strong>的—— 上下文为空即抛
 * {@code MissingTenantContextException}。本模块原先只在 {@code JwtAuthenticationFilter} 里写租户上下文， 而该过滤器只在
 * {@code security.enabled=true} 时才被注册；开发 / 测试 profile 默认 {@code security.enabled=false}，
 * 于是过滤器整条不进链、没有任何组件写租户上下文，所有经 HTTP 的建模读路径（建字段回读实体、列表、详情、运行时 CRUD） 一律
 * 500。故这里用一条<strong>始终注册</strong>的拦截器兜底，与安全开关解耦，取值顺序与平台完全一致：{@code X-Tenant-Id} 头优先。
 *
 * <p><b>清除时机</b>：{@code afterCompletion} 里 {@link TenantContext#clear()}，避免线程池复用污染下一个请求。 拦截器运行在
 * Servlet Filter（含 {@code JwtAuthenticationFilter}）之后、Controller 之前，所以在 Controller 执行期间
 * 上下文一定已就绪；若请求未带 {@code X-Tenant-Id}，上下文保持 null，由 SDK 按 ADR-0029 失败关闭——这正是对越权读取的预期拒绝。
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

  private static final String TENANT_ID_HEADER = "X-Tenant-Id";

  /** 标记本次请求是否由本拦截器建立了租户上下文，仅在这种情况下 afterCompletion 才负责清理。 */
  private static final String ATTR_TENANT_SET = "bone.metadata.tenantSetByInterceptor";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String tenantId = request.getHeader(TENANT_ID_HEADER);
    if (tenantId != null && !tenantId.isBlank()) {
      TenantContext.setTenantId(tenantId);
      request.setAttribute(ATTR_TENANT_SET, Boolean.TRUE);
    }
    // 头缺失时<strong>不</strong>兜底默认值：保留 ADR-0029 的失败关闭——下游 SDK 读到空上下文会拒绝读写，
    // 而不是退化成「按某个默认租户查询」。生产环境由网关在每个请求上盖章 X-Tenant-Id，故该分支只对应越权/畸形入站。
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    // 仅清理本拦截器建立的上下文：避免覆盖测试或上游已显式设置的租户上下文（如 @BeforeEach 一次性设置的场景），
    // 同时生产环境每请求带头、每请求清理，线程复用无泄漏。
    if (Boolean.TRUE.equals(request.getAttribute(ATTR_TENANT_SET))) {
      TenantContext.clear();
    }
  }
}
