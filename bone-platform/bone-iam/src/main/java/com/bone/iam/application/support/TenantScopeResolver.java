package com.bone.iam.application.support;

import com.bone.iam.domain.gateway.TenantProvider;

/**
 * 租户过滤解析（详设 §3.4 / §4.8，S-4 去重）。
 *
 * <p>原逻辑散落在 Account / Menu / Role / Audit / Dept 五个 ApplicationService 的私有方法里，逐字节相同—— 单测与后续口径调整都要改
 * 5 处，易漂移。抽到此处成为租户口径单一真源。
 *
 * <p>设计为无状态的静态工具方法（而非注入 Bean）：解析逻辑是 {@code TenantProvider} 与查询参数的纯函数， 各 ApplicationService 本就持有
 * {@code TenantProvider}，直接传入即可；这样不引入新的构造参数， 避免破坏直接 {@code new XxxApplicationService(...)} 的单元测试（如
 * {@code MenuApplicationServiceTreeKeywordTest}）。
 *
 * <p>规则：调用方非平台租户（&gt; 0）⇒ 强制按其过滤（租户隔离不可被查询参数绕过）； 平台租户（0）/无租户上下文（平台管理员代操作）⇒ 回退到查询参数，未传则不加过滤。
 */
public final class TenantScopeResolver {

  private TenantScopeResolver() {}

  public static Long resolve(TenantProvider tenantProvider, Long fromQuery) {
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    return fromQuery;
  }
}
