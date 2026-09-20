package com.bone.iam.infrastructure.gateway;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.domain.gateway.TenantProvider;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID。
 *
 * <p><b>E-2 的落点</b>：{@code TenantContext} 只允许在基础设施层直调；application / domain / adapter
 * 一律经端口访问。这样租户读取可被单测 mock，也把「审计、传播、空值防护」收敛到唯一实现里， 而不是散落在每个查询处理器中。
 *
 * <p><b>不做默认值兜底</b>：与 metadata-server 的 {@code TenantProviderGatewayAdapter} 不同，这里不把缺失的租户 回退成
 * {@code 1}——IAM 的多张表都以 {@code tenant_id} 为隔离键，凭空造一个租户会让「未认证查询」 被静默翻译成「以某租户身份查询」。缺失就是 {@code
 * null}，由调用方决定放行还是拒绝。
 */
@Component
public class TenantProviderGatewayAdapter implements TenantProvider {

  @Override
  public Long currentTenantIdOrNull() {
    return TenantContext.getTenantIdAsLong();
  }
}
