package com.bone.integration.infrastructure.gateway;

import com.bone.core.tenant.context.TenantContext;
import com.bone.platform.alert.domain.gateway.TenantProvider;
import org.springframework.stereotype.Component;

/**
 * 通知模块 {@link TenantProvider} 端口的桥接实现。
 *
 * <p><b>修复背景</b>：{@code IntegrationNotificationConfig} 在 {@code integration.alert.enabled=true} 时
 * {@code @Import(AlertAutoConfiguration.class)}，后者装配的 {@code AlertService} 需要 {@code
 * com.bone.platform.alert.domain.gateway.TenantProvider} 类型的 bean。integration 模块此前只提供了 自身 {@code
 * com.bone.integration.domain.gateway.TenantProvider} 的实现，缺少通知模块这一接口的 bean，
 * 导致应用上下文启动失败（NoSuchBeanDefinitionException）。本适配器补齐该 bean，从可信租户上下文读取当前租户， 与模块内 {@code
 * TenantProviderAdapter} 行为一致。
 */
@Component
public class AlertTenantProviderAdapter implements TenantProvider {

  @Override
  public Long currentTenantIdOrNull() {
    return TenantContext.getTenantIdAsLong();
  }
}
