package com.bone.iam.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.iam.domain.gateway.TenantProvider;
import org.junit.jupiter.api.Test;

/**
 * 租户隔离口径单一真源（安全相关）的纯函数单测（M-1）。
 *
 * <p>规则：调用方非平台租户（&gt; 0）⇒ 强制按其过滤（不可被查询参数绕过）；平台租户（0）/ 无租户上下文 ⇒ 回退到查询参数。
 */
class TenantScopeResolverTest {

  private final TenantProvider provider = mock(TenantProvider.class);

  @Test
  void contextTenantOverridesQueryParam() {
    when(provider.currentTenantIdOrNull()).thenReturn(7L);
    assertThat(TenantScopeResolver.resolve(provider, 99L)).isEqualTo(7L);
  }

  @Test
  void platformTenantFallsBackToQueryParam() {
    when(provider.currentTenantIdOrNull()).thenReturn(0L);
    assertThat(TenantScopeResolver.resolve(provider, 99L)).isEqualTo(99L);
  }

  @Test
  void nullContextFallsBackToQueryParam() {
    when(provider.currentTenantIdOrNull()).thenReturn(null);
    assertThat(TenantScopeResolver.resolve(provider, 99L)).isEqualTo(99L);
  }

  @Test
  void platformTenantWithNullQueryReturnsNull() {
    when(provider.currentTenantIdOrNull()).thenReturn(0L);
    assertThat(TenantScopeResolver.resolve(provider, null)).isNull();
  }
}
