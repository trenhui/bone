package com.bone.platform.alert.infrastructure.gateway;

import com.bone.core.tenant.context.TenantContext;
import com.bone.platform.alert.domain.gateway.TenantProvider;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID。
 *
 * <p><b>N-1 的落点</b>：{@code TenantContext} 只允许在基础设施层直调；application / domain / adapter
 * 一律经端口访问，这样租户读取可被单测 mock，也把「空值防护」收敛到唯一实现里。
 *
 * <p><b>不做默认值兜底</b>：缺失就是 {@code null}，由调用方决定放行还是拒绝——凭空造一个租户会让 「未认证访问」被静默翻译成「以某租户身份访问」。
 *
 * <p><b>类名带 {@code Alert} 前缀</b>：本模块是内嵌库，会与宿主服务（bone-system 等）的同名适配器 一起被组件扫描，默认 Bean
 * 名会冲突（ConflictingBeanDefinitionException），故用前缀区分。
 */
@Component("alertTenantProviderGatewayAdapter")
public class AlertTenantProviderGatewayAdapter implements TenantProvider {

  @Override
  public Long currentTenantIdOrNull() {
    return TenantContext.getTenantIdAsLong();
  }
}
