package com.bone.engine.extension.studio.infrastructure.gateway;

import com.bone.core.tenant.context.TenantContext;
import com.bone.engine.extension.studio.domain.gateway.TenantProvider;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供租户访问能力（E-2）。
 *
 * <p>{@code TenantContext} 只允许在基础设施层直调；application / domain / adapter 一律经端口访问， 使租户读取可被单测
 * mock，并把审计、传播与空值防护收敛到唯一实现。
 *
 * <p><b>作用域为何必须在此闭合</b>：{@code TenantContext} 基于 {@code ThreadLocal}， 「设租户 → 执行业务 → 恢复」必须成对出现在同一个
 * {@code try/finally} 里。若把 set/clear 拆给应用层 分别调用，业务分支一旦提前 return 或抛异常就会泄漏上下文，污染同线程的后续请求。
 */
@Component
public class TenantProviderAdapter implements TenantProvider {

  @Override
  public Long currentTenantIdOrNull() {
    return TenantContext.getTenantIdAsLong();
  }

  @Override
  public <T> T runAs(Long tenantId, Supplier<T> action) {
    Long previous = TenantContext.getTenantIdAsLong();
    TenantContext.setTenantId(tenantId);
    try {
      return action.get();
    } finally {
      if (previous != null) {
        TenantContext.setTenantId(previous);
      } else {
        TenantContext.clear();
      }
    }
  }
}
