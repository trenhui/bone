package com.bone.claim.infrastructure.tenant;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.core.tenant.context.TenantContext;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器（出站端口实现，必须 {@code *PortAdapter} 命名——ArchUnit 门禁）。
 *
 * <p>认证主体为租户唯一权威来源（§10.3）：框架过滤器已把 token 的租户 claim 写入 {@link TenantContext}， 本适配器只读取，不信任请求头。
 */
@Component
public class TenantPortAdapter implements TenantPort {

  @Override
  public long currentTenantId() {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("租户上下文缺失：请求须经认证过滤器写入 TenantContext");
    }
    return TenantContext.getTenantIdAsLong();
  }
}
